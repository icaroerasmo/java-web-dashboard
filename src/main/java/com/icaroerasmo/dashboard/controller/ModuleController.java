package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.service.ModuleHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ModuleController {

    private final ModuleHealthService healthService;
    private final DashboardProperties properties;
    private final RestClient restClient;

    public ModuleController(ModuleHealthService healthService, DashboardProperties properties, RestClient.Builder builder) {
        this.healthService = healthService;
        this.properties = properties;
        this.restClient = builder.build();
    }

    @GetMapping("/modules")
    public Map<String, List<Map<String, String>>> getModules() {
        return Map.of("modules", healthService.getModules());
    }

    @GetMapping("/modules/{name}/config")
    public ResponseEntity<?> getConfig(@PathVariable String name) {
        String baseUrl = findBaseUrl(name);
        if (baseUrl == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return restClient.get()
                    .uri(baseUrl + "/api/config")
                    .retrieve()
                    .toEntity(Map.class);
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }

    @PutMapping("/modules/{name}/config")
    public ResponseEntity<?> updateConfig(@PathVariable String name, @RequestBody Map<String, Object> config) {
        String baseUrl = findBaseUrl(name);
        if (baseUrl == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return restClient.put()
                    .uri(baseUrl + "/api/config")
                    .body(config)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }

    @PostMapping("/modules/{name}/restart")
    public ResponseEntity<?> restartModule(@PathVariable String name) {
        String baseUrl = findBaseUrl(name);
        if (baseUrl == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Map<String, Object> config = restClient.get()
                    .uri(baseUrl + "/api/config")
                    .retrieve()
                    .body(Map.class);
            if (config == null) {
                return ResponseEntity.status(502).build();
            }
            return restClient.put()
                    .uri(baseUrl + "/api/config")
                    .body(config)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }

    @GetMapping("/go2rtc/streams")
    public ResponseEntity<?> getGo2RtcStreams() {
        try {
            return restClient.get()
                    .uri(properties.getGo2rtc().getBaseUrl() + "/api/streams")
                    .retrieve()
                    .toEntity(Map.class);
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }

    private String findBaseUrl(String name) {
        return properties.getModules().stream()
                .filter(module -> module.getName().equals(name))
                .map(DashboardProperties.Module::getBaseUrl)
                .findFirst()
                .orElse(null);
    }
}