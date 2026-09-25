package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.messaging.NotificationSummary;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Serves the notification history and proxies media stored on Telegram (via the
 * notifier). The notifier is the only service with the Telegram bot token.
 */
@Log4j2
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final RestClient restClient;
    private final String notifierBaseUrl;

    public NotificationController(RestClient.Builder restClientBuilder,
                                  @Value("${dashboard.notifier.base-url:http://java-telegram-notifier:8080}") String notifierBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.notifierBaseUrl = notifierBaseUrl;
    }

    @GetMapping("/notifications")
    public List<NotificationSummary> getNotifications(@RequestParam(defaultValue = "100") int limit,
                                                      @RequestParam(required = false) Long before) {
        try {
            String url = notifierBaseUrl + "/api/notifications?limit={limit}";
            Object[] params;
            if (before != null) {
                url += "&before={before}";
                params = new Object[]{limit, before};
            } else {
                params = new Object[]{limit};
            }
            NotificationSummary[] summaries = restClient.get()
                    .uri(url, params)
                    .retrieve()
                    .body(NotificationSummary[].class);
            return summaries != null ? List.of(summaries) : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch notifications from notifier: {}", e.getMessage());
            return List.of();
        }
    }

    @GetMapping("/notifications/media/{fileId}")
    public ResponseEntity<byte[]> getMedia(@PathVariable String fileId,
                                           @RequestParam(value = "filename", required = false) String filename) {
        try {
            String url = notifierBaseUrl + "/api/media/" + fileId;
            if (filename != null && !filename.isBlank()) {
                url += "?filename=" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8);
            }
            return restClient.get()
                    .uri(java.net.URI.create(url))
                    .retrieve()
                    .toEntity(byte[].class);
        } catch (Exception e) {
            log.warn("Failed to fetch media {} from notifier: {}", fileId, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }
}
