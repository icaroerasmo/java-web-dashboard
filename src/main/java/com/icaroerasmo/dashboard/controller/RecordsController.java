package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.dto.RecordsPage;
import com.icaroerasmo.dashboard.service.RecordsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * Browses recorded camera videos (backed by the Elasticsearch {@code records}
 * index) and streams a single recording for playback. Listing/population data
 * comes from the recorder, which owns the rclone/Drive/local storage.
 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordsController {

    private final RecordsService recordsService;

    /** Triggers the Elasticsearch population of video records (local + Google Drive). */
    @PostMapping("/populate")
    public ResponseEntity<Map<String, Object>> populate() {
        int indexed = recordsService.populate();
        if (indexed < 0) {
            return ResponseEntity.status(502).body(Map.of("error", "Population failed or already running"));
        }
        return ResponseEntity.ok(Map.of("indexed", indexed));
    }

    /** Distinct camera names present in the index. */
    @GetMapping("/cameras")
    public List<String> cameras() {
        return recordsService.listCameras();
    }

    /** Cursor-based page of recordings, newest first. */
    @GetMapping
    public RecordsPage list(@RequestParam(required = false) String camera,
                            @RequestParam(defaultValue = "100") int limit,
                            @RequestParam(required = false) String cursor) {
        return recordsService.search(camera, limit, cursor);
    }

    /** Streams a single recording (proxied to the recorder, which handles local/Drive). */
    @GetMapping("/{filename}/stream")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable String filename) {
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        StreamingResponseBody body = outputStream -> recordsService.streamFromRecorder(filename, outputStream);
        return ResponseEntity.ok().contentType(mediaType).body(body);
    }
}
