/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import jakarta.json.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.wildfly.extension.mcp.MCPLogger;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

/**
 * Tracks pending server-initiated requests (e.g. {@code elicitation/create}) and routes
 * client responses back to the waiting {@link CompletableFuture}.
 *
 * <p>Each {@link org.wildfly.extension.mcp.api.MCPConnection} owns one registry instance.
 * Request IDs are generated starting at 10 000 to avoid collisions with client-supplied
 * tool-call IDs (which typically start at 1).</p>
 */
public class PendingRequestRegistry {

    private final AtomicLong idGenerator = new AtomicLong(10_000L);
    private final ConcurrentMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();

    /**
     * Reserves a new request ID and registers the given future.
     * The caller should block on the returned future.
     *
     * @param future the future to complete when the client responds
     * @return the allocated request ID (must be included in the outgoing JSON-RPC message)
     */
    public long register(CompletableFuture<JsonObject> future) {
        long id = idGenerator.incrementAndGet();
        pending.put(id, future);
        return id;
    }

    /**
     * Called by {@link MCPMessageHandler} when a client response arrives.
     * Completes the registered future if one exists for the given id; silently discards otherwise.
     *
     * @param rawId   the {@code "id"} field from the JSON-RPC response (may be a JsonValue)
     * @param message the full JSON-RPC response message
     */
    public void handleResponse(Object rawId, JsonObject message) {
        if (rawId == null) {
            ROOT_LOGGER.debugf("Client response has no id, discarding: %s", message);
            return;
        }
        long id;
        try {
            id = coerceId(rawId);
        } catch (NumberFormatException e) {
            ROOT_LOGGER.debugf("Client response id '%s' is not a long, discarding", rawId);
            return;
        }
        CompletableFuture<JsonObject> future = pending.remove(id);
        if (future == null) {
            ROOT_LOGGER.debugf("No pending request for id %s, discarding client response", id);
            return;
        }
        future.complete(message);
    }

    /**
     * Removes the pending future for {@code id} without completing it.
     * Used for timeout cleanup.
     *
     * @return {@code true} if a pending entry was removed
     */
    public boolean remove(long id) {
        return pending.remove(id) != null;
    }

    /**
     * Coerce the raw id (a {@link jakarta.json.JsonValue} rendered via {@code toString()}) to a long.
     * JSON number 10001 → {@code "10001"}; JSON string "10001" → {@code "\"10001\""}.
     */
    private long coerceId(Object rawId) {
        String s = rawId.toString();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return Long.parseLong(s);
    }
}
