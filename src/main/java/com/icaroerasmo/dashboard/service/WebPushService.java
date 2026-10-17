package com.icaroerasmo.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.messaging.NotificationSummary;
import com.icaroerasmo.dashboard.messaging.PushSubscription;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends Web Push notifications to subscribed browsers. Uses the Web Push
 * protocol (RFC 8291) with VAPID so notifications work even when the PWA is
 * closed — unlike the WebSocket feed, which only works while the page is open.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class WebPushService {

    private final DashboardProperties properties;
    private final PushSubscriptionStore store;
    private final ObjectMapper objectMapper;

    private PushService pushService;
    private long sendTimeoutSeconds = 15L;

    @PostConstruct
    void init() {
        DashboardProperties.Push push = properties.getPush();
        if (push.getPublicKey() == null || push.getPublicKey().isBlank()
                || push.getPrivateKey() == null || push.getPrivateKey().isBlank()) {
            log.warn("Web Push disabled: VAPID keys not configured");
            return;
        }
        if (push.getSendTimeoutSeconds() > 0) {
            this.sendTimeoutSeconds = push.getSendTimeoutSeconds();
        }
        try {
            Security.addProvider(new BouncyCastleProvider());
            this.pushService = new PushService(
                    push.getPublicKey(), push.getPrivateKey(), push.getSubject());
            log.info("Web Push enabled");
        } catch (GeneralSecurityException e) {
            log.error("Failed to initialize Web Push: {}", e.getMessage(), e);
            this.pushService = null;
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public void broadcast(NotificationSummary summary) {
        if (pushService == null || summary == null) {
            return;
        }
        if (!summary.browserNotify()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "title", "Cafofo",
                    "body", summary.summary() != null ? summary.summary() : "",
                    "tag", summary.id() != null ? summary.id() : "",
                    "id", summary.id() != null ? summary.id() : ""));
        } catch (Exception e) {
            log.warn("Failed to serialize push payload: {}", e.getMessage());
            return;
        }

        for (PushSubscription sub : store.all()) {
            if (sub == null || isBlank(sub.endpoint())
                    || isBlank(sub.p256dh()) || isBlank(sub.auth())) {
                log.warn("Removing push subscription with missing keys");
                store.remove(sub);
                continue;
            }
            try {
                Notification notification = new Notification(
                        sub.endpoint(), sub.p256dh(), sub.auth(), payload);
                HttpResponse response = sendWithTimeout(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    log.info("Web Push delivered to {}: status={}", sub.endpoint(), status);
                } else {
                    log.warn("Web Push rejected for {}: status={}", sub.endpoint(), status);
                }
                if (status == 404 || status == 410) {
                    log.warn("Push subscription gone ({}), removing", status);
                    store.remove(sub);
                }
            } catch (TimeoutException e) {
                log.warn("Push send timed out after {}s for subscription {}",
                        sendTimeoutSeconds, sub.endpoint());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while sending push notification");
                return;
            } catch (GeneralSecurityException | IOException | JoseException | ExecutionException e) {
                log.warn("Failed to send push notification, removing subscription: {}", e.getMessage());
                store.remove(sub);
            } catch (RuntimeException e) {
                log.warn("Failed to send push notification (runtime), removing subscription: {}", e.getMessage());
                store.remove(sub);
            }
        }
    }

    /**
     * Sends a push notification with a bounded wait. The Web Push client makes an
     * HTTP call to the browser vendor's push service (FCM, Mozilla autopush, etc.);
     * if that endpoint hangs, we must not block the worker thread forever — a
     * single hung subscription used to stall the whole listener.
     */
    private HttpResponse sendWithTimeout(Notification notification)
            throws GeneralSecurityException, IOException, JoseException,
            ExecutionException, InterruptedException, TimeoutException {
        Future<HttpResponse> future = pushService.sendAsync(notification);
        return future.get(sendTimeoutSeconds, TimeUnit.SECONDS);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
