package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.messaging.NotificationPage;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    public NotificationPage getNotifications(@RequestParam(defaultValue = "notifications") String type,
                                             @RequestParam(defaultValue = "100") int limit,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) String text,
                                             @RequestParam(required = false) String kind,
                                             @RequestParam(required = false) String date,
                                             @RequestParam(required = false) String hour) {
        try {
            StringBuilder url = new StringBuilder(notifierBaseUrl)
                    .append("/api/notifications?type=").append(type)
                    .append("&limit=").append(limit);
            appendParam(url, "cursor", cursor);
            appendParam(url, "text", text);
            appendParam(url, "kind", kind);
            appendParam(url, "date", date);
            appendParam(url, "hour", hour);
            NotificationPage page = restClient.get()
                    .uri(java.net.URI.create(url.toString()))
                    .retrieve()
                    .body(NotificationPage.class);
            return page != null ? page : new NotificationPage(List.of(), null, false);
        } catch (Exception e) {
            log.warn("Failed to fetch notifications from notifier: {}", e.getMessage());
            return new NotificationPage(List.of(), null, false);
        }
    }

    private void appendParam(StringBuilder url, String name, String value) {
        if (value != null && !value.isBlank()) {
            url.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/notifications/kinds")
    public List<String> getKinds() {
        try {
            String[] kinds = restClient.get()
                    .uri(java.net.URI.create(notifierBaseUrl + "/api/notifications/kinds"))
                    .retrieve()
                    .body(String[].class);
            return kinds != null ? List.of(kinds) : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch kinds from notifier: {}", e.getMessage());
            return List.of();
        }
    }

    @GetMapping("/notifications/media/{fileId}")
    public ResponseEntity<byte[]> getMedia(@PathVariable String fileId,
                                           @RequestParam(value = "filename", required = false) String filename) {
        try {
            String url = notifierBaseUrl + "/api/media/" + fileId;
            if (filename != null && !filename.isBlank()) {
                url += "?filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
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
