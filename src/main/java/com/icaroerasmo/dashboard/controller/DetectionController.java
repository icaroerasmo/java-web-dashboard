package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.service.DetectionStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/detections")
@RequiredArgsConstructor
public class DetectionController {

    private final DetectionStateService detectionStateService;

    @GetMapping
    public ResponseEntity<List<DetectionDto>> getDetections() {
        Map<String, String> active = detectionStateService.activeDetections();
        List<DetectionDto> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : active.entrySet()) {
            result.add(new DetectionDto(entry.getKey(), entry.getValue()));
        }
        return ResponseEntity.ok(result);
    }

    public record DetectionDto(String cameraName, String label) {
    }
}