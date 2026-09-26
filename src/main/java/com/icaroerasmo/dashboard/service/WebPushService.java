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

    @PostConstruct
    void init() {
        DashboardProperties.Push push = properties.getPush();
        if (push.getPublicKey() == null || push.getPublicKey().isBlank()
                || push.getPrivateKey() == null || push.getPrivateKey().isBlank()) {
            log.warn("Web Push disabled: VAPID keys not configured");
            return;
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
            try {
                Notification notification = new Notification(
                        sub.endpoint(), sub.p256dh(), sub.auth(), payload);
                HttpResponse response = pushService.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    log.warn("Push subscription gone ({}), removing", status);
                    store.remove(sub);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while sending push notification");
                return;
            } catch (GeneralSecurityException | IOException | JoseException | ExecutionException e) {
                log.warn("Failed to send push notification, removing subscription: {}", e.getMessage());
                store.remove(sub);
            }
        }
    }
}
