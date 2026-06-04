/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import io.undertow.util.HttpString;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Registry of active MCP client connections for a single deployed MCP endpoint.
 * <p>
 * Manages the full lifecycle of {@link MCPConnection} objects: creation, lookup, idle-connection
 * cleanup, and graceful shutdown with optional pre-shutdown notification broadcast. One instance
 * is created per deployment and wired into the Undertow handler chain.
 * </p>
 */
public class ConnectionManager {

    public static final HttpString MCP_SESSION_ID_HEADER = HttpString.tryFromString("mcp-session-id");
    public static final HttpString MCP_PROTOCOL_VERSION_HEADER = HttpString.tryFromString("mcp-protocol-version");
    private final ConcurrentMap<String, MCPConnection> connections = new ConcurrentHashMap<>();
    private ScheduledFuture<?> cleanupTask;

    /**
     * Generates a new unique, URL-safe session ID for a client connection.
     *
     * @return a base64url-encoded random session identifier
     */
    public String id() {
        return Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the connection with the given session ID, or {@code null} if not found.
     *
     * @param id the session ID to look up
     * @return the matching {@link MCPConnection}, or {@code null}
     */
    public MCPConnection get(String id) {
        return connections.get(id);
    }

    /**
     * Registers a new connection in the pool. The connection's {@link MCPConnection#id()} is used as the key.
     *
     * @param connection the connection to register
     */
    public void add(MCPConnection connection) {
        connections.put(connection.id(), connection);
    }

    /**
     * Removes and closes the connection with the given session ID.
     *
     * @param id the session ID of the connection to remove
     * @return {@code true} if the connection existed and was successfully closed; {@code false} otherwise
     */
    public boolean remove(String id) {
        MCPConnection connection = connections.remove(id);
        if (connection != null) {
            try {
                connection.close();
                return true;
            } catch (IOException ex) {
                ROOT_LOGGER.errorClosingConnection(ex, id);
            }
        }
        return false;
    }

    /**
     * Starts a periodic cleanup task that closes connections idle for longer than {@code timeoutSeconds}.
     *
     * @param timeoutSeconds idle timeout in seconds; connections with no activity within this window are closed
     * @param scheduler the executor used to schedule the recurring cleanup
     */
    public void start(long timeoutSeconds, ScheduledExecutorService scheduler) {
        cleanupTask = scheduler.scheduleAtFixedRate(() -> cleanup(timeoutSeconds), timeoutSeconds, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Cancels the periodic cleanup task without closing any open connections.
     */
    public synchronized void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel(true);
            cleanupTask = null;
        }
    }

    /**
     * Stops the cleanup task and closes all active connections.
     */
    public synchronized void shutdown() {
        stop();
        for (String id : new ArrayList<>(connections.keySet())) {
            remove(id);
        }
    }

    /**
     * Broadcasts a JSON-RPC notification to all active connections (IN_OPERATION status).
     * <p>
     * Only connections that implement {@link Responder} and are in the {@link MCPConnection.Status#IN_OPERATION}
     * state will receive the notification. Connections that fail to receive the notification are logged
     * but not removed from the connection pool.
     * </p>
     * <p>
     * This method does not close connections. For broadcasting with subsequent connection cleanup,
     * use {@link #broadcastThenShutdown(JsonObject...)}.
     * </p>
     *
     * @param notifications one or more JSON-RPC notifications to broadcast to all active connections
     * @see #broadcastThenShutdown(JsonObject...) for broadcast with connection cleanup
     */
    public void broadcast(JsonObject... notifications) {
        List<MCPConnection> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(connections.values());
        }
        for (MCPConnection connection : snapshot) {
            if (connection.status() == MCPConnection.Status.IN_OPERATION && connection instanceof Responder responder) {
                for (JsonObject notification : notifications) {
                    try {
                        responder.send(notification);
                    } catch (RuntimeException e) {
                        ROOT_LOGGER.errorBroadcastingNotification(e, connection.id());
                    }
                }
            }
        }
    }

    /**
     * Broadcasts multiple JSON-RPC notifications to all active connections, then closes all connections.
     * <p>
     * This method is fully synchronized to ensure that:
     * <ul>
     * <li>All notifications are delivered before any connection is closed</li>
     * <li>No concurrent shutdown or cleanup can interfere with notification delivery</li>
     * <li>The cleanup task is stopped before broadcasting begins</li>
     * </ul>
     * </p>
     * <p>
     * Only connections in {@link MCPConnection.Status#IN_OPERATION} state that implement
     * {@link Responder} will receive the notifications. After all notifications are sent,
     * all connections (regardless of status) are closed and removed from the connection pool.
     * </p>
     * <p>
     * This method is typically called during deployment undeploy to notify clients of
     * resource/prompt list changes before the server shuts down the MCP endpoint.
     * </p>
     *
     * @param notifications one or more JSON-RPC notifications to broadcast before shutdown
     * @see #broadcast(JsonObject) for broadcasting without closing connections
     */
    public void broadcastThenShutdown(JsonObject... notifications) {
        List<MCPConnection> snapshot;
        synchronized (this) {
            stop();
            snapshot = new ArrayList<>(connections.values());
        }
        // I/O outside the lock — cleanup/add/get are not blocked
        for (MCPConnection connection : snapshot) {
            if (connection.status() == MCPConnection.Status.IN_OPERATION && connection instanceof Responder responder) {
                for (JsonObject notification : notifications) {
                    try {
                        responder.sendSync(notification);
                    } catch (RuntimeException e) {
                        ROOT_LOGGER.errorBroadcastingShutdownNotification(e, connection.id());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        ROOT_LOGGER.errorBroadcastingShutdownNotification(e, connection.id());
                    }
                }
            }
        }
        // Re-acquire for teardown
        synchronized (this) {
            for (String id : new ArrayList<>(connections.keySet())) {
                try {
                    remove(id);
                } catch (RuntimeException e) {
                    ROOT_LOGGER.errorClosingConnection(e, id);
                }
            }
        }
    }

    synchronized void cleanup(long timeoutSeconds) {
        long cutoff = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeoutSeconds);
        List<String> stale = new ArrayList<>();
        for (ConcurrentMap.Entry<String, MCPConnection> entry : connections.entrySet()) {
            if (entry.getValue().lastActivity() < cutoff) {
                stale.add(entry.getKey());
            }
        }
        for (String id : stale) {
            ROOT_LOGGER.closingStaleConnection(id);
            remove(id);
        }
    }
}
