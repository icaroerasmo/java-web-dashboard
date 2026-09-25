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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/rabbitmq")
@RequiredArgsConstructor
public class RabbitMqController {

    private static final String VHOST = "%2F";

    private final DashboardProperties properties;
    private final RestClient.Builder builder;
    private final EnvService envService;
    private RestClient restClient;

    @PostConstruct
    void init() {
        this.restClient = builder.build();
    }

    @GetMapping("/queues")
    public ResponseEntity<?> getQueues() {
        try {
            List<Map> queues = restClient.get()
                    .uri(URI.create(managementUrl() + "/api/queues"))
                    .headers(headers -> setAuth(headers))
                    .retrieve()
                    .body(List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            if (queues != null) {
                for (Map queue : queues) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", queue.get("name"));
                    item.put("messages", numberOrZero(queue.get("messages")));
                    item.put("messages_ready", numberOrZero(queue.get("messages_ready")));
                    item.put("messages_unacknowledged", numberOrZero(queue.get("messages_unacknowledged")));
                    item.put("consumers", numberOrZero(queue.get("consumers")));
                    result.add(item);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Failed to list rabbitmq queues: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @GetMapping("/queues/{queue}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String queue, @RequestParam(defaultValue = "10") int count) {
        try {
            Map<String, Object> body = Map.of(
                    "count", count,
                    "ackmode", "ack_requeue_true",
                    "encoding", "auto");
            List<?> messages = restClient.post()
                    .uri(URI.create(managementUrl() + "/api/queues/" + VHOST + "/" + encode(queue) + "/get"))
                    .headers(headers -> setAuth(headers))
                    .body(body)
                    .retrieve()
                    .body(List.class);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.warn("Failed to read messages from rabbitmq queue '{}': {}", queue, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @PostMapping("/queues/{queue}/messages")
    public ResponseEntity<?> sendMessage(@PathVariable String queue, @RequestBody Map<String, String> request) {
        try {
            Map<String, Object> body = Map.of(
                    "properties", Map.of(),
                    "routing_key", queue,
                    "payload", request.getOrDefault("payload", ""),
                    "payload_encoding", "string");
            Map<?, ?> response = restClient.post()
                    .uri(URI.create(managementUrl() + "/api/exchanges/" + VHOST + "/amq.default/publish"))
                    .headers(headers -> setAuth(headers))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to send message to rabbitmq queue '{}': {}", queue, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @DeleteMapping("/queues/{queue}/messages")
    public ResponseEntity<?> removeMessages(@PathVariable String queue, @RequestParam(required = false) Integer count) {
        try {
            if (count != null) {
                Map<String, Object> body = Map.of(
                        "count", count,
                        "ackmode", "ack_requeue_false",
                        "encoding", "auto");
return restClient.post()
                    .uri(URI.create(managementUrl() + "/api/queues/" + VHOST + "/" + encode(queue) + "/get"))
                    .headers(headers -> setAuth(headers))
                    .body(body)
                    .retrieve()
                    .toEntity(List.class);
            }
            return restClient.delete()
                    .uri(URI.create(managementUrl() + "/api/queues/" + VHOST + "/" + encode(queue) + "/contents"))
                    .headers(headers -> setAuth(headers))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to remove messages from rabbitmq queue '{}': {}", queue, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @PostMapping("/restart")
    public ResponseEntity<?> restart() {
        try {
            envService.restartService("rabbitmq");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Failed to restart rabbitmq: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    private String managementUrl() {
        return properties.getRabbitmq().getManagementUrl();
    }

    private Object numberOrZero(Object value) {
        return value != null ? value : 0;
    }

    private void setAuth(org.springframework.http.HttpHeaders headers) {
        headers.setBasicAuth(
                properties.getRabbitmq().getUsername(),
                properties.getRabbitmq().getPassword());
    }

    private String encode(String queue) {
        return UriUtils.encodePathSegment(queue, StandardCharsets.UTF_8);
    }
}