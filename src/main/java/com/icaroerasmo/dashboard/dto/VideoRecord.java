package com.icaroerasmo.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A recorded video segment as returned by the recorder's {@code /api/records}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VideoRecord(
        String camera,
        String filename,
        String timestamp,
        String drivePath,
        String localPath,
        long size,
        boolean local
) {}
