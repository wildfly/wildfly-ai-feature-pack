/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages per-connection subscriptions for the {@code subscriptions/listen} method
 * introduced in the 2026-07-28 protocol version.
 * <p>
 * Each call to {@link #listen} replaces the entire subscription set for that connection —
 * it is not additive. An empty list clears all subscriptions.
 * </p>
 * <p>
 * <b>Constraint:</b> {@code notifications/message} (logging) MUST NOT be delivered on
 * subscription streams. This manager only tracks resource subscriptions; logging
 * notifications should be filtered at the delivery layer.
 * </p>
 */
class SubscriptionManager {

    record SubscriptionTarget(String type, String uri) {}

    private final Map<String, Set<SubscriptionTarget>> connectionSubscriptions = new ConcurrentHashMap<>();

    void listen(String connectionId, Set<SubscriptionTarget> subscriptions) {
        if (subscriptions.isEmpty()) {
            connectionSubscriptions.remove(connectionId);
        } else {
            Set<SubscriptionTarget> newSet = ConcurrentHashMap.newKeySet();
            newSet.addAll(subscriptions);
            connectionSubscriptions.put(connectionId, newSet);
        }
    }

    boolean isSubscribed(String connectionId, String type, String uri) {
        Set<SubscriptionTarget> subs = connectionSubscriptions.get(connectionId);
        return subs != null && subs.contains(new SubscriptionTarget(type, uri));
    }

    Set<SubscriptionTarget> getSubscriptions(String connectionId) {
        return connectionSubscriptions.getOrDefault(connectionId, Collections.emptySet());
    }

    void forEachSubscriber(String type, String uri, Consumer<String> action) {
        SubscriptionTarget target = new SubscriptionTarget(type, uri);
        for (Map.Entry<String, Set<SubscriptionTarget>> entry : connectionSubscriptions.entrySet()) {
            if (entry.getValue().contains(target)) {
                action.accept(entry.getKey());
            }
        }
    }

    void removeConnection(String connectionId) {
        connectionSubscriptions.remove(connectionId);
    }
}
