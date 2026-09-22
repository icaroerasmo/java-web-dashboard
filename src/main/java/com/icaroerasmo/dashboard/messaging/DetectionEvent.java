package com.icaroerasmo.dashboard.messaging;

import java.util.List;

public record DetectionEvent(
        String eventId,
        String cameraName,
        String template,
        List<String> args) {
}