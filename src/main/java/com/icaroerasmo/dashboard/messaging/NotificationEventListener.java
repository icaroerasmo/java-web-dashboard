package com.icaroerasmo.dashboard.messaging;

import com.icaroerasmo.dashboard.config.RabbitMqConfig;
import com.icaroerasmo.dashboard.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationWebSocketHandler notificationWebSocketHandler;

    @RabbitListener(queues = RabbitMqConfig.DASHBOARD_NOTIFICATIONS_QUEUE)
    public void onNotification(NotificationSummary summary) {
        if (summary == null || summary.id() == null || summary.id().isBlank()) {
            log.warn("Ignoring invalid notification summary");
            return;
        }
        log.debug("Notification summary received: id={}, mediaType={}, kind={}", summary.id(), summary.mediaType(), summary.kind());
        notificationWebSocketHandler.push(summary);
    }
}
