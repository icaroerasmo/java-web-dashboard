package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ModuleHealthService {

    private final DashboardProperties properties;
    private final RestClient restClient;

    public ModuleHealthService(DashboardProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public List<Map<String, String>> getModules() {
        List<Map<String, String>> modules = new ArrayList<>();
        for (DashboardProperties.Module module : properties.getModules()) {
            modules.add(moduleStatus(module.getName(), "java", checkJava(module.getBaseUrl())));
        }
        modules.add(moduleStatus("rabbitmq", "infra", checkRabbitMq()));
        modules.add(moduleStatus("go2rtc", "infra", checkGo2Rtc()));
        return modules;
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