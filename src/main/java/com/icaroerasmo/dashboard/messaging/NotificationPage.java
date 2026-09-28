package com.icaroerasmo.dashboard.messaging;

import java.util.List;

/**
 * A page of notifications plus an opaque cursor for the next page and a flag
 * signalling whether more pages exist. Mirrors the notifier's contract.
 */
public record NotificationPage(List<NotificationSummary> items, String nextCursor, boolean hasMore) {
}
