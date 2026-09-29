package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.service.EnvService;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch config + cluster status. The "configuration" values (endpoint URI
 * and log TTL) are the notifier's {@code spring.elasticsearch.uris} and
 * {@code elasticsearch.log-ttl-days} — stored literally (they may hold
 * {@code ${VAR}} / {@code ${VAR:-default}} references, like every other module
 * config) and resolved at runtime by the notifier. They are proxied through the
 * notifier's {@code /api/config} endpoint.
 */
@Log4j2
@RestController
@RequestMapping("/api/elasticsearch")
public class ElasticsearchController {

    private static final String DEFAULT_ES_URI = "${ELASTICSEARCH_URL:http://elasticsearch:9200}";
    private static final String DEFAULT_ES_TTL = "${ELASTICSEARCH_LOG_TTL_DAYS:10}";

    private final DashboardProperties properties;
    private final EnvService envService;
    private final String notifierBaseUrl;
    private final RestClient.Builder builder;
    private RestClient restClient;

    public ElasticsearchController(DashboardProperties properties,
                                   EnvService envService,
                                   RestClient.Builder builder,
                                   @Value("${dashboard.notifier.base-url:http://java-telegram-notifier:8080}") String notifierBaseUrl) {
        this.properties = properties;
        this.envService = envService;
        this.builder = builder;
        this.notifierBaseUrl = notifierBaseUrl;
    }

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
        Map<String, Object> notifier = fetchNotifierConfig();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", nested(notifier, DEFAULT_ES_URI, "spring", "elasticsearch", "uris"));
        result.put("ttlDays", nested(notifier, DEFAULT_ES_TTL, "elasticsearch", "log-ttl-days"));
        result.put("indexNotifications", "notifications");
        result.put("indexLogs", "logs");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> notifier = fetchNotifierConfig();
            if (notifier == null) {
                return ResponseEntity.status(502).body(Map.of("error", "Notifier config unavailable"));
            }
            Object url = request.get("baseUrl");
            if (url != null && !String.valueOf(url).isBlank()) {
                setNested(notifier, String.valueOf(url).trim(), "spring", "elasticsearch", "uris");
            }
            Object ttl = request.get("ttlDays");
            if (ttl != null && !String.valueOf(ttl).isBlank()) {
                setNested(notifier, String.valueOf(ttl).trim(), "elasticsearch", "log-ttl-days");
            }
            updateNotifierConfig(notifier);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to update elasticsearch config: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
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

    private Map<String, Object> fetchNotifierConfig() {
        try {
            return restClient.get()
                    .uri(notifierBaseUrl + "/api/config")
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Failed to fetch notifier config: {}", e.getMessage());
            return null;
        }
    }

    private void updateNotifierConfig(Map<String, Object> config) {
        restClient.put()
                .uri(notifierBaseUrl + "/api/config")
                .body(config)
                .retrieve()
                .toBodilessEntity();
    }

    private String baseUrl() {
        String reference = nested(fetchNotifierConfig(), DEFAULT_ES_URI, "spring", "elasticsearch", "uris");
        String resolved = envService.resolveEnvRef(reference);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return properties.getElasticsearch().getBaseUrl();
    }

    @SuppressWarnings("unchecked")
    private String nested(Map<String, Object> map, String defaultValue, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) {
                return defaultValue;
            }
            current = ((Map<String, Object>) current).get(key);
        }
        return current != null ? String.valueOf(current) : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private void setNested(Map<String, Object> map, String value, String... keys) {
        Map<String, Object> current = map;
        for (int i = 0; i < keys.length - 1; i++) {
            Object child = current.get(keys[i]);
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                current.put(keys[i], child);
            }
            current = (Map<String, Object>) child;
        }
        current.put(keys[keys.length - 1], value);
    }
}
