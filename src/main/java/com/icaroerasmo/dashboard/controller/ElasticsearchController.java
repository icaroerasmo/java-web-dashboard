package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.service.EnvService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/elasticsearch")
@RequiredArgsConstructor
public class ElasticsearchController {

    private final DashboardProperties properties;
    private final RestClient.Builder builder;
    private final EnvService envService;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = builder.build();
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            Map health = restClient.get()
                    .uri(baseUrl() + "/_cluster/health")
                    .retrieve()
                    .body(Map.class);
            if (health != null) {
                result.putAll(health);
            }
            Map root = restClient.get()
                    .uri(baseUrl() + "/")
                    .retrieve()
                    .body(Map.class);
            if (root != null) {
                Object version = root.get("version");
                if (version instanceof Map<?, ?> versionMap) {
                    result.put("version", versionMap.get("number"));
                }
                result.put("name", root.get("name"));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Failed to read elasticsearch health: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @GetMapping("/indices")
    public ResponseEntity<?> indices() {
        try {
            List<Map> indices = restClient.get()
                    .uri(baseUrl() + "/_cat/indices?format=json&h=index,docs.count,store.size,health,status")
                    .retrieve()
                    .body(List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            if (indices != null) {
                for (Map index : indices) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", index.get("index"));
                    item.put("docsCount", index.get("docs.count"));
                    item.put("storeSize", index.get("store.size"));
                    item.put("health", index.get("health"));
                    item.put("status", index.get("status"));
                    result.add(item);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Failed to list elasticsearch indices: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        DashboardProperties.Elasticsearch es = properties.getElasticsearch();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", es.getBaseUrl());
        result.put("ttlDays", es.getTtlDays());
        result.put("indexNotifications", "notifications");
        result.put("indexLogs", "logs");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/restart")
    public ResponseEntity<?> restart() {
        try {
            envService.restartService("elasticsearch");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to restart elasticsearch: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    private String baseUrl() {
        return properties.getElasticsearch().getBaseUrl();
    }
}
