package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModuleHealthService {

    private final DashboardProperties properties;
    private final RestClient restClient;
    private final Map<String, String> statusCache = new ConcurrentHashMap<>();

    public ModuleHealthService(DashboardProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Scheduled(fixedRate = 5000)
    public void refreshStatus() {
        for (DashboardProperties.Module module : properties.getModules()) {
            statusCache.put(module.getName(), checkJava(module.getBaseUrl()));
        }
        statusCache.put("rabbitmq", checkRabbitMq());
        statusCache.put("go2rtc", checkGo2Rtc());
    }

    public List<Map<String, String>> getModules() {
        if (statusCache.isEmpty()) {
            refreshStatus();
        }
        List<Map<String, String>> modules = new ArrayList<>();
        for (DashboardProperties.Module module : properties.getModules()) {
            modules.add(moduleStatus(module.getName(), "java", statusOf(module.getName())));
        }
        modules.add(moduleStatus("rabbitmq", "infra", statusOf("rabbitmq")));
        modules.add(moduleStatus("go2rtc", "infra", statusOf("go2rtc")));
        return modules;
    }

    private String statusOf(String name) {
        return statusCache.getOrDefault(name, "DOWN");
    }

    private Map<String, String> moduleStatus(String name, String type, String status) {
        Map<String, String> module = new LinkedHashMap<>();
        module.put("name", name);
        module.put("type", type);
        module.put("status", status);
        return module;
    }

    private String checkJava(String baseUrl) {
        try {
            ResponseEntity<Map> response = restClient.get()
                    .uri(baseUrl + "/actuator/health")
                    .retrieve()
                    .toEntity(Map.class);
            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && "UP".equals(response.getBody().get("status"))) {
                return "UP";
            }
        } catch (Exception ignored) {
        }
        return "DOWN";
    }

    private String checkRabbitMq() {
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri(properties.getRabbitmq().getManagementUrl() + "/api/health/checks/alarms")
                    .headers(headers -> headers.setBasicAuth(
                            properties.getRabbitmq().getUsername(),
                            properties.getRabbitmq().getPassword()))
                    .retrieve()
                    .toBodilessEntity();
            if (response.getStatusCode().is2xxSuccessful()) {
                return "UP";
            }
        } catch (Exception ignored) {
        }
        return "DOWN";
    }

    private String checkGo2Rtc() {
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri(properties.getGo2rtc().getBaseUrl() + "/api/streams")
                    .retrieve()
                    .toBodilessEntity();
            if (response.getStatusCode().is2xxSuccessful()) {
                return "UP";
            }
        } catch (Exception ignored) {
        }
        return "DOWN";
    }
}