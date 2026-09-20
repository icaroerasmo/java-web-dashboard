package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.model.EnvVar;
import com.icaroerasmo.dashboard.service.EnvService;
import com.icaroerasmo.dashboard.service.ModuleHealthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleHealthService healthService;
    private final DashboardProperties properties;
    private final RestClient.Builder builder;
    private final EnvService envService;
    private RestClient restClient;

    @PostConstruct
    void init() {
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

    @GetMapping("/modules/{name}/env")
    public ResponseEntity<?> getEnv(@PathVariable String name) {
        if (findBaseUrl(name) == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(envService.getEnvVars(name));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/modules/{name}/env")
    public ResponseEntity<?> updateEnv(@PathVariable String name, @RequestBody List<EnvVar> envVars) {
        if (findBaseUrl(name) == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            envService.updateEnvVars(name, envVars);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/env")
    public ResponseEntity<?> getGlobalEnv() {
        try {
            return ResponseEntity.ok(envService.getGlobalEnvVars());
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/env")
    public ResponseEntity<?> updateGlobalEnv(@RequestBody List<EnvVar> envVars) {
        try {
            envService.updateGlobalEnvVars(envVars);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
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