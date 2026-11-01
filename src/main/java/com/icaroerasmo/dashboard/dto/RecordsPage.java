package com.icaroerasmo.dashboard.dto;

import java.util.List;

/**
 * A page of video records as returned by {@code GET /api/records}, cursor-based
 * for infinite scroll.
 */
public record RecordsPage(
        List<VideoRecord> items,
        String nextCursor,
        boolean hasMore
) {}
