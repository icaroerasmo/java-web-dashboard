package com.icaroerasmo.dashboard.controller;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.messaging.PushSubscription;
import com.icaroerasmo.dashboard.service.PushSubscriptionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final DashboardProperties properties;
    private final PushSubscriptionStore store;

    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        String key = properties.getPush().getPublicKey();
        return Map.of("publicKey", key != null ? key : "");
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@RequestBody PushSubscription subscription) {
        if (subscription == null || subscription.endpoint() == null || subscription.endpoint().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        store.add(subscription);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody PushSubscription subscription) {
        if (subscription != null) {
            store.remove(subscription);
        }
        return ResponseEntity.ok().build();
    }
}
