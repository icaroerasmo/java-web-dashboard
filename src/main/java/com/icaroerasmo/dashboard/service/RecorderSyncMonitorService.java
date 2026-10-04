package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.messaging.NotificationSummary;
import com.icaroerasmo.dashboard.websocket.NotificationWebSocketHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls the recorder's {@code /actuator/sync} endpoint and alerts the browser
 * when the recorder is unreachable or its last sync is too old. The overdue
 * threshold is 1.5x the recorder's sync interval, so a recorder that simply
 * stopped syncing (or went offline) triggers a notification.
 */
@Log4j2
@Service
public class RecorderSyncMonitorService {

    private static final double OVERDUE_FACTOR = 1.5;
    private static final long CHECK_INTERVAL_MS = 60_000L;

    private final DashboardProperties properties;
    private final RestClient restClient;
    private final WebPushService webPushService;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final AtomicBoolean alerting = new AtomicBoolean(false);

    public RecorderSyncMonitorService(DashboardProperties properties,
                                      RestClient.Builder restClientBuilder,
                                      WebPushService webPushService,
                                      NotificationWebSocketHandler notificationWebSocketHandler) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.webPushService = webPushService;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
    }

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = 120_000L)
    public void checkRecorderSync() {
        DashboardProperties.Recorder recorder = properties.getRecorder();

        long lastSync = 0;
        int intervalMinutes = recorder.getSyncIntervalMinutes();
        boolean reachable = false;

        try {
            ResponseEntity<Map> response = restClient.get()
                    .uri(recorder.getBaseUrl() + "/actuator/sync")
                    .retrieve()
                    .toEntity(Map.class);
            if (response.getBody() != null) {
                reachable = true;
                if (response.getBody().get("lastSyncEpochMillis") instanceof Number n) {
                    lastSync = n.longValue();
                }
                if (response.getBody().get("syncIntervalMinutes") instanceof Number n) {
                    intervalMinutes = n.intValue();
                }
            }
        } catch (Exception e) {
            reachable = false;
        }

        String kind;
        String summary;
        if (!reachable) {
            kind = "RECORDER_OFFLINE";
            summary = "Recorder indisponível — a sincronização pode estar interrompida.";
        } else if (lastSync == 0) {
            // Recorder acabou de subir e ainda não sincronizou: período de graça.
            alerting.set(false);
            return;
        } else {
            long thresholdMs = (long) (OVERDUE_FACTOR * intervalMinutes * 60_000L);
            long elapsedMs = System.currentTimeMillis() - lastSync;
            if (elapsedMs <= thresholdMs) {
                alerting.set(false);
                return;
            }
            long elapsedMinutes = elapsedMs / 60_000L;
            kind = "SYNC_OVERDUE";
            summary = "Sincronização atrasada — último sync há " + elapsedMinutes + " min (limite "
                    + Math.round(OVERDUE_FACTOR * intervalMinutes) + " min).";
        }

        if (alerting.compareAndSet(false, true)) {
            notifyBrowser(kind, summary);
        }
    }

    private void notifyBrowser(String kind, String summary) {
        long now = System.currentTimeMillis();
        NotificationSummary notification = new NotificationSummary(
                UUID.randomUUID().toString(),
                "dashboard",
                "TEXT",
                kind,
                summary,
                null,
                null,
                null,
                now,
                Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                String.valueOf(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).getHour()),
                0L,
                List.of(),
                true);

        webPushService.broadcast(notification);
        notificationWebSocketHandler.push(notification);
        log.info("Recorder sync alert triggered: kind={} summary={}", kind, summary);
    }
}
