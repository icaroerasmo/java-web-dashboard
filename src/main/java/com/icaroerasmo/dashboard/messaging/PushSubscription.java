package com.icaroerasmo.dashboard.messaging;

/**
 * A Web Push subscription sent by the browser. {@code p256dh} and {@code auth}
 * are the client keys used by the Web Push protocol to encrypt the payload.
 */
public record PushSubscription(
        String endpoint,
        String p256dh,
        String auth) {
}
