package com.icaroerasmo.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.messaging.PushSubscription;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of browser Web Push subscriptions. Persisted to disk so the
 * subscriptions survive a container restart — an in-memory registry is wiped on
 * every restart and browsers would silently stop receiving notifications until
 * they re-open the dashboard.
 */
@Log4j2
@Service
public class PushSubscriptionStore {

    private final Set<PushSubscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final Path subscriptionsFile;

    public PushSubscriptionStore(ObjectMapper objectMapper, DashboardProperties properties) {
        this.objectMapper = objectMapper;
        this.subscriptionsFile = Paths.get(properties.getPush().getSubscriptionsFile());
        load();
    }

    public void add(PushSubscription subscription) {
        subscriptions.add(subscription);
        persist();
    }

    public void remove(PushSubscription subscription) {
        subscriptions.remove(subscription);
        persist();
    }

    public List<PushSubscription> all() {
        return List.copyOf(subscriptions);
    }

    public int size() {
        return subscriptions.size();
    }

    private void load() {
        if (subscriptionsFile == null || !Files.isReadable(subscriptionsFile)) {
            log.info("No persisted push subscriptions at {}", subscriptionsFile);
            return;
        }
        try {
            List<PushSubscription> loaded = objectMapper.readValue(
                    subscriptionsFile.toFile(),
                    new TypeReference<List<PushSubscription>>() {});
            subscriptions.addAll(loaded);
            log.info("Loaded {} push subscriptions from {}", loaded.size(), subscriptionsFile);
        } catch (IOException e) {
            log.warn("Failed to load push subscriptions from {}: {}", subscriptionsFile, e.getMessage());
        }
    }

    private void persist() {
        if (subscriptionsFile == null) {
            return;
        }
        try {
            Path parent = subscriptionsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = subscriptionsFile.resolveSibling(subscriptionsFile.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), new ArrayList<>(subscriptions));
            Files.move(tmp, subscriptionsFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to persist push subscriptions to {}: {}", subscriptionsFile, e.getMessage());
        }
    }
}
