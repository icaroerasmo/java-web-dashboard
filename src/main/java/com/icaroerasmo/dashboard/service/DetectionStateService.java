package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.messaging.DetectionEvent;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Component
public class DetectionStateService {

    private static final long TTL_MS = 5_000;

    private final Map<String, DetectionState> states = new ConcurrentHashMap<>();

    public void update(DetectionEvent event) {
        if (event == null || event.cameraName() == null || event.cameraName().isBlank()) {
            log.warn("Ignoring invalid detection event");
            return;
        }
        try {
            Object[] args = event.args() != null ? event.args().toArray() : new Object[0];
            String label = LabelTranslator.translate(event.template(), args);
            states.put(event.cameraName(), new DetectionState(label, System.currentTimeMillis()));
            log.debug("Detection state updated: camera={}, label={}", event.cameraName(), label);
        } catch (Exception e) {
            log.warn("Failed to update detection state for camera '{}': {}", event.cameraName(), e.getMessage());
        }
    }

    public Map<String, String> activeDetections() {
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(e -> now - e.getValue().detectedAt() > TTL_MS);

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, DetectionState> entry : states.entrySet()) {
            result.put(entry.getKey(), entry.getValue().label());
        }
        return result;
    }

    public boolean isActive(String cameraName) {
        long now = System.currentTimeMillis();
        DetectionState state = states.get(cameraName);
        return state != null && now - state.detectedAt() <= TTL_MS;
    }

    public void removeExpired() {
        activeDetections();
    }

    @Scheduled(fixedDelayString = "5000")
    void sweep() {
        activeDetections();
    }

    public record DetectionState(String label, long detectedAt) {
    }
}