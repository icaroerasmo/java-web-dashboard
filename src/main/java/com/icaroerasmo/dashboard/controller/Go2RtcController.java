package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.service.EnvService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/go2rtc")
@RequiredArgsConstructor
public class Go2RtcController {

    private final DashboardProperties properties;
    private final RestClient.Builder builder;
    private final EnvService envService;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = builder.build();
    }

    @GetMapping("/streams")
    public ResponseEntity<?> getStreams() {
        try {
            Map<String, Map<String, Object>> streams = restClient.get()
                    .uri(properties.getGo2rtc().getBaseUrl() + "/api/streams")
                    .retrieve()
                    .body(Map.class);
            List<Map<String, String>> result = new ArrayList<>();
            if (streams != null) {
                for (Map.Entry<String, Map<String, Object>> entry : streams.entrySet()) {
                    String name = entry.getKey();
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("url", sourceUrl(entry.getValue()));
                    result.add(item);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Failed to list go2rtc streams: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @PutMapping("/streams")
    public ResponseEntity<?> saveStream(@RequestBody Map<String, String> request) {
        String name = request.getOrDefault("name", "");
        String url = request.getOrDefault("url", "");
        if (name.isBlank() || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            restClient.put()
                    .uri(properties.getGo2rtc().getBaseUrl() + "/api/streams?name="
                            + encodeQuery(name) + "&src=" + encodeQuery(url))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                    })
                    .toBodilessEntity();
            if (streamExists(name)) {
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.status(502).build();
        } catch (Exception e) {
            log.warn("Failed to save go2rtc stream '{}': {}", name, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @DeleteMapping("/streams/{name}")
    public ResponseEntity<?> removeStream(@PathVariable String name) {
        try {
            restClient.delete()
                    .uri(properties.getGo2rtc().getBaseUrl() + "/api/streams?src=" + encodeQuery(name))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                    })
                    .toBodilessEntity();
            if (!streamExists(name)) {
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.status(502).build();
        } catch (Exception e) {
            log.warn("Failed to remove go2rtc stream '{}': {}", name, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @PostMapping("/restart")
    public ResponseEntity<?> restart() {
        try {
            envService.restartService("go2rtc");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to restart go2rtc: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    private boolean streamExists(String name) {
        try {
            Map<String, Map<String, Object>> streams = restClient.get()
                    .uri(properties.getGo2rtc().getBaseUrl() + "/api/streams")
                    .retrieve()
                    .body(Map.class);
            return streams != null && streams.containsKey(name);
        } catch (Exception e) {
            return false;
        }
    }

    private String sourceUrl(Map<String, Object> stream) {
        Object producers = stream.get("producers");
        if (producers instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object url = first.get("url");
            if (url != null) {
                return String.valueOf(url);
            }
        }
        return "";
    }

    private String encodeQuery(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }
}