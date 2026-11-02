package com.icaroerasmo.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.dto.RecordsPage;
import com.icaroerasmo.dashboard.dto.VideoRecord;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Populates the Elasticsearch {@code records} index with video metadata gathered
 * from the recorder ({@code /api/records}: local folder + Google Drive via rclone).
 * Runs on a schedule and can be triggered manually through {@code POST /api/records/populate}.
 */
@Service
@Log4j2
public class RecordsService {

    private static final String INDEX = "records";

    private final DashboardProperties properties;
    private final EnvService envService;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder builder;
    private final String recorderBaseUrl;
    private final AtomicBoolean populating = new AtomicBoolean(false);
    private RestClient restClient;

    public RecordsService(DashboardProperties properties,
                          EnvService envService,
                          ObjectMapper objectMapper,
                          RestClient.Builder builder,
                          @Value("${dashboard.recorder.base-url:http://java-rtsp-recorder:8080}") String recorderBaseUrl) {
        this.properties = properties;
        this.envService = envService;
        this.objectMapper = objectMapper;
        this.builder = builder;
        this.recorderBaseUrl = recorderBaseUrl;
    }

    @PostConstruct
    void init() {
        this.restClient = builder.build();
    }

    @Scheduled(fixedDelay = 15 * 60_000L, initialDelay = 60_000L)
    public void scheduledPopulate() {
        populate();
    }

    /** @return number of records indexed, 0 when empty, -1 on failure/already running. */
    public int populate() {
        if (!populating.compareAndSet(false, true)) {
            return -1;
        }
        try {
            List<VideoRecord> records = fetchFromRecorder();
            if (records == null || records.isEmpty()) {
                return 0;
            }
            ensureIndex();
            bulkIndex(records);
            int deleted = reconcile(records);
            log.info("Records indexed: {}, deleted: {}", records.size(), deleted);
            return records.size();
        } catch (Exception e) {
            log.warn("Failed to populate records: {}", e.getMessage());
            return -1;
        } finally {
            populating.set(false);
        }
    }

    private List<VideoRecord> fetchFromRecorder() {
        JsonNode array = restClient.get()
                .uri(java.net.URI.create(recorderBaseUrl + "/api/records"))
                .retrieve()
                .body(JsonNode.class);
        if (array == null) {
            return List.of();
        }
        return objectMapper.convertValue(array, new TypeReference<>() {});
    }

    private void ensureIndex() {
        Map<String, Object> mapping = Map.of(
                "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                "mappings", Map.of("properties", Map.of(
                        "camera", Map.of("type", "keyword"),
                        "filename", Map.of("type", "keyword"),
                        "timestamp", Map.of("type", "date", "format", "yyyy-MM-dd HH:mm:ss"),
                        "year", Map.of("type", "integer"),
                        "month", Map.of("type", "integer"),
                        "drivePath", Map.of("type", "keyword"),
                        "localPath", Map.of("type", "keyword"),
                        "size", Map.of("type", "long"),
                        "local", Map.of("type", "boolean")
                ))
        );
        try {
            restClient.put()
                    .uri(java.net.URI.create(esBaseUrl() + "/" + INDEX))
                    .body(mapping)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // "resource_already_exists_exception" on re-runs is expected.
            log.debug("Index {} already exists or ES unavailable: {}", INDEX, e.getMessage());
        }
    }

    private void bulkIndex(List<VideoRecord> records) {
        StringBuilder body = new StringBuilder(records.size() * 220);
        for (VideoRecord r : records) {
            body.append("{\"index\":{\"_id\":\"").append(escapeJson(r.filename())).append("\"}}\n");
            try {
                body.append(objectMapper.writeValueAsString(toDoc(r))).append('\n');
            } catch (Exception e) {
                log.warn("Failed to serialize record {}: {}", r.filename(), e.getMessage());
            }
        }
        restClient.post()
                .uri(java.net.URI.create(esBaseUrl() + "/" + INDEX + "/_bulk"))
                .header("Content-Type", "application/x-ndjson")
                .body(body.toString())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Deletes Elasticsearch documents whose filename is no longer present in the
     * recorder's current list (local + Drive). This happens only after a
     * successful full fetch, so a transient Drive failure (which would make the
     * recorder throw and abort {@link #populate()}) never triggers deletions.
     */
    private int reconcile(List<VideoRecord> records) {
        java.util.Set<String> current = records.stream()
                .map(VideoRecord::filename)
                .collect(java.util.stream.Collectors.toSet());
        List<String> stale = listAllIds().stream()
                .filter(id -> !current.contains(id))
                .toList();
        if (!stale.isEmpty()) {
            bulkDelete(stale);
        }
        return stale.size();
    }

    private List<String> listAllIds() {
        List<String> ids = new ArrayList<>();
        List<Object> after = null;
        while (true) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("size", 1000);
            body.put("_source", false);
            body.put("sort", List.of(Map.of("filename", Map.of("order", "asc"))));
            if (after != null) {
                body.put("search_after", after);
            }
            try {
                JsonNode resp = restClient.post()
                        .uri(java.net.URI.create(esBaseUrl() + "/" + INDEX + "/_search"))
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                JsonNode hits = resp.path("hits").path("hits");
                if (hits.isEmpty()) {
                    break;
                }
                for (JsonNode hit : hits) {
                    ids.add(hit.path("_id").asText());
                    JsonNode sort = hit.path("sort");
                    if (sort.isArray() && !sort.isEmpty()) {
                        after = jsonArrayToList(sort);
                    }
                }
                if (hits.size() < 1000) {
                    break;
                }
            } catch (Exception e) {
                log.warn("Failed to list index ids: {}", e.getMessage());
                return List.of();
            }
        }
        return ids;
    }

    private void bulkDelete(List<String> ids) {
        for (int i = 0; i < ids.size(); i += 500) {
            List<String> chunk = ids.subList(i, Math.min(i + 500, ids.size()));
            StringBuilder body = new StringBuilder(chunk.size() * 60);
            for (String id : chunk) {
                body.append("{\"delete\":{\"_id\":\"").append(escapeJson(id)).append("\"}}\n");
            }
            try {
                restClient.post()
                        .uri(java.net.URI.create(esBaseUrl() + "/" + INDEX + "/_bulk"))
                        .header("Content-Type", "application/x-ndjson")
                        .body(body.toString())
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.warn("Failed to delete stale records: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> toDoc(VideoRecord r) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("camera", r.camera());
        doc.put("filename", r.filename());
        doc.put("timestamp", r.timestamp());
        doc.put("year", yearOf(r.timestamp()));
        doc.put("month", monthOf(r.timestamp()));
        doc.put("drivePath", r.drivePath());
        doc.put("localPath", r.localPath());
        doc.put("size", r.size());
        doc.put("local", r.local());
        return doc;
    }

    private static int yearOf(String ts) {
        return ts != null && ts.length() >= 4 ? Integer.parseInt(ts.substring(0, 4)) : 0;
    }

    private static int monthOf(String ts) {
        return ts != null && ts.length() >= 7 ? Integer.parseInt(ts.substring(5, 7)) : 0;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String esBaseUrl() {
        String url = envService.getEnvValue("ELASTICSEARCH_URL");
        return (url != null && !url.isBlank()) ? url : properties.getElasticsearch().getBaseUrl();
    }

    /** Searches recorded videos, newest first, with cursor-based pagination. */
    public RecordsPage search(String camera, String date, String hour, int limit, String cursor) {
        if (isFutureDate(date)) {
            log.debug("Ignoring records search with future date: {}", date);
            return new RecordsPage(List.of(), null, false);
        }
        int size = Math.max(1, Math.min(limit, 500));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", size);

        List<Map<String, Object>> filters = new ArrayList<>();
        if (camera != null && !camera.isBlank()) {
            filters.add(Map.of("term", Map.of("camera", camera)));
        }
        Map<String, Object> timestampRange = timestampRange(date, hour);
        if (timestampRange != null) {
            filters.add(Map.of("range", timestampRange));
        }

        Map<String, Object> query = new LinkedHashMap<>();
        if (filters.isEmpty()) {
            query.put("match_all", Map.of());
        } else {
            query.put("bool", Map.of("filter", filters));
        }
        body.put("query", query);
        body.put("sort", List.of(
                Map.of("timestamp", Map.of("order", "desc")),
                Map.of("filename", Map.of("order", "desc"))));

        if (cursor != null && !cursor.isBlank()) {
            body.put("search_after", decodeCursor(cursor));
        }

        try {
            JsonNode resp = restClient.post()
                    .uri(java.net.URI.create(esBaseUrl() + "/" + INDEX + "/_search"))
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            List<VideoRecord> items = new ArrayList<>();
            String nextCursor = null;
            JsonNode hits = resp.path("hits").path("hits");
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                VideoRecord record = objectMapper.treeToValue(source, VideoRecord.class);
                items.add(record);
                JsonNode sort = hit.path("sort");
                if (sort.isArray() && !sort.isEmpty()) {
                    nextCursor = encodeCursor(sort);
                }
            }
            boolean hasMore = items.size() == size;
            return new RecordsPage(items, nextCursor, hasMore);
        } catch (Exception e) {
            log.warn("Failed to search records: {}", e.getMessage());
            return new RecordsPage(List.of(), null, false);
        }
    }

    private static Map<String, Object> timestampRange(String date, String hour) {
        if (date == null || date.isBlank()) {
            return null;
        }
        String hh = (hour != null && !hour.isBlank())
                ? (hour.trim().length() == 1 ? "0" + hour.trim() : hour.trim())
                : null;
        String start = date + " " + (hh != null ? hh : "00") + ":00:00";
        String end = date + " " + (hh != null ? hh : "23") + ":59:59";
        return Map.of("timestamp", Map.of("gte", start, "lte", end));
    }

    /**
     * Returns true when {@code date} is strictly after today, in which case a
     * filter makes no sense (no future recordings can exist).
     */
    private static boolean isFutureDate(String date) {
        if (date == null || date.isBlank()) {
            return false;
        }
        try {
            java.time.LocalDate parsed = java.time.LocalDate.parse(date.trim());
            return parsed.isAfter(java.time.LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    /** Proxies a video thumbnail to the recorder (generated via ffmpeg, cached). */
    public byte[] fetchThumbnail(String filename) {
        String url = recorderBaseUrl + "/api/records/" + filename + "/thumbnail";
        return restClient.get()
                .uri(java.net.URI.create(url))
                .retrieve()
                .body(byte[].class);
    }

    /** Distinct camera names present in the index, sorted. */
    public List<String> listCameras() {
        Map<String, Object> body = Map.of(
                "size", 0,
                "aggs", Map.of("cameras", Map.of(
                        "terms", Map.of("field", "camera", "size", 1000,
                                "order", Map.of("_key", "asc")))));
        try {
            JsonNode resp = restClient.post()
                    .uri(java.net.URI.create(esBaseUrl() + "/" + INDEX + "/_search"))
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            List<String> cameras = new ArrayList<>();
            for (JsonNode bucket : resp.path("aggregations").path("cameras").path("buckets")) {
                cameras.add(bucket.path("key").asText());
            }
            return cameras;
        } catch (Exception e) {
            log.warn("Failed to list cameras: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Streams a single recording from the recorder to the given output stream.
     * The recorder serves local files directly (with Range support) or downloads
     * them on the fly from Google Drive via rclone.
     */
    public void streamFromRecorder(String filename, java.io.OutputStream outputStream) throws java.io.IOException {
        String url = recorderBaseUrl + "/api/records/" + filename + "/stream";
        restClient.get()
                .uri(java.net.URI.create(url))
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new java.io.IOException("Recorder returned " + response.getStatusCode());
                    }
                    response.getBody().transferTo(outputStream);
                    return null;
                });
    }

    private static String encodeCursor(JsonNode sortArray) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sortArray.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<Object> decodeCursor(String cursor) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode array = objectMapper.readTree(json);
            return jsonArrayToList(array);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Object> jsonArrayToList(JsonNode array) {
        List<Object> values = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.isNumber()) {
                values.add(node.asLong());
            } else {
                values.add(node.asText());
            }
        }
        return values;
    }
}
