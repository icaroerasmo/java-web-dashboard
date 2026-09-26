package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.messaging.PushSubscription;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of browser Web Push subscriptions. Kept in memory so the
 * dashboard can broadcast push notifications to every subscribed device.
 */
@Service
public class PushSubscriptionStore {

    private final Set<PushSubscription> subscriptions = ConcurrentHashMap.newKeySet();

    public void add(PushSubscription subscription) {
        subscriptions.add(subscription);
    }

    public void remove(PushSubscription subscription) {
        subscriptions.remove(subscription);
    }

    public List<PushSubscription> all() {
        return List.copyOf(subscriptions);
    }

    public int size() {
        return subscriptions.size();
    }
}
