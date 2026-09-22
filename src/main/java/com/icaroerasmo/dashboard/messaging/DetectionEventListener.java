package com.icaroerasmo.dashboard.messaging;

import com.icaroerasmo.dashboard.config.RabbitMqConfig;
import com.icaroerasmo.dashboard.service.DetectionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class DetectionEventListener {

    private final DetectionStateService detectionStateService;

    @RabbitListener(queues = RabbitMqConfig.DASHBOARD_DETECTION_QUEUE)
    public void onDetection(DetectionEvent event) {
        if (event == null || event.cameraName() == null || event.cameraName().isBlank()) {
            log.warn("Ignoring invalid detection event");
            return;
        }
        log.debug("Detection event received: camera={}, template={}", event.cameraName(), event.template());
        detectionStateService.update(event);
    }
}