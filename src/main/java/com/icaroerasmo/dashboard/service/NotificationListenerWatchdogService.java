package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.messaging.NotificationSummary;
import com.icaroerasmo.dashboard.websocket.NotificationWebSocketHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watchdog do listener de notificações. Periodicamente consulta (via RabbitAdmin)
 * quantos consumidores existem na fila {@code dashboard.notifications}; se zerar,
 * o listener parou de consumir (falha silenciosa) e o watchdog faz self-heal
 * (stop/start do container) e avisa o navegador via Web Push + feed.
 */
@Log4j2
@Service
public class NotificationListenerWatchdogService {

    private static final String NOTIFICATION_QUEUE = "dashboard.notifications";

    private final RabbitAdmin rabbitAdmin;
    private final RabbitListenerEndpointRegistry registry;
    private final WebPushService webPushService;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final AtomicBoolean alerting = new AtomicBoolean(false);

    public NotificationListenerWatchdogService(RabbitAdmin rabbitAdmin,
                                               RabbitListenerEndpointRegistry registry,
                                               WebPushService webPushService,
                                               NotificationWebSocketHandler notificationWebSocketHandler) {
        this.rabbitAdmin = rabbitAdmin;
        this.registry = registry;
        this.webPushService = webPushService;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void checkNotificationListener() {
        int consumers = currentConsumerCount();
        if (consumers < 0) {
            return; // não foi possível determinar; não age
        }

        if (consumers > 0) {
            alerting.set(false);
            return;
        }

        log.error("Notification listener DOWN (0 consumers on '{}'). Attempting self-heal...", NOTIFICATION_QUEUE);
        restartNotificationListener();

        if (alerting.compareAndSet(false, true)) {
            notifyBrowser();
        }
    }

    private int currentConsumerCount() {
        try {
            Properties props = rabbitAdmin.getQueueProperties(NOTIFICATION_QUEUE);
            if (props == null) {
                log.warn("Queue '{}' not found via RabbitAdmin", NOTIFICATION_QUEUE);
                return -1;
            }
            Object value = props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT);
            if (value instanceof Number n) {
                return n.intValue();
            }
            if (value != null) {
                return Integer.parseInt(value.toString());
            }
            return -1;
        } catch (Exception e) {
            log.warn("Failed to query consumer count for '{}': {}", NOTIFICATION_QUEUE, e.getMessage());
            return -1;
        }
    }

    private void restartNotificationListener() {
        try {
            for (MessageListenerContainer container : registry.getListenerContainers()) {
                if (container instanceof AbstractMessageListenerContainer amlc
                        && amlc.getQueueNames() != null
                        && Arrays.asList(amlc.getQueueNames()).contains(NOTIFICATION_QUEUE)) {
                    log.warn("Restarting notification listener container...");
                    amlc.stop();
                    amlc.start();
                    log.info("Notification listener container restarted");
                    return;
                }
            }
            log.warn("Notification listener container not found in registry");
        } catch (Exception e) {
            log.error("Failed to restart notification listener container", e);
        }
    }

    private void notifyBrowser() {
        long now = System.currentTimeMillis();
        NotificationSummary summary = new NotificationSummary(
                UUID.randomUUID().toString(),
                "dashboard",
                "TEXT",
                "NOTIFICATION_LISTENER_DOWN",
                "Atenção: o listener de notificações do dashboard ficou inativo e foi reiniciado automaticamente.",
                null,
                null,
                null,
                now,
                Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                String.valueOf(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).getHour()),
                0L,
                List.of(),
                true);

        webPushService.broadcast(summary);
        notificationWebSocketHandler.push(summary);
        log.info("Notification listener watchdog alert sent");
    }
}
