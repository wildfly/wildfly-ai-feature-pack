/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import io.undertow.util.HttpString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionManager {

    public static final HttpString MCP_SESSION_ID_HEADER = HttpString.tryFromString("mcp-session-id");
    public static final HttpString MCP_PROTOCOL_VERSION_HEADER = HttpString.tryFromString("mcp-protocol-version");
    private final ConcurrentMap<String, MCPConnection> connections = new ConcurrentHashMap<>();
    private ScheduledFuture<?> cleanupTask;

    public String id() {
        return Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
    }

    public MCPConnection get(String id) {
        return connections.get(id);
    }

    public void add(MCPConnection connection) {
        connections.put(connection.id(), connection);
    }

    public boolean remove(String id) {
        MCPConnection connection = connections.remove(id);
        if (connection != null) {
            try {
                connection.close();
                return true;
            } catch (IOException ex) {
                Logger.getLogger(ConnectionManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }

    public void start(long timeoutSeconds, ScheduledExecutorService scheduler) {
        cleanupTask = scheduler.scheduleAtFixedRate(() -> cleanup(timeoutSeconds), timeoutSeconds, timeoutSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel(true);
            cleanupTask = null;
        }
    }

    void cleanup(long timeoutSeconds) {
        long cutoff = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeoutSeconds);
        List<String> stale = new ArrayList<>();
        for (ConcurrentMap.Entry<String, MCPConnection> entry : connections.entrySet()) {
            if (entry.getValue().lastActivity() < cutoff) {
                stale.add(entry.getKey());
            }
        }
        for (String id : stale) {
            Logger.getLogger(ConnectionManager.class.getName()).log(Level.INFO,
                    "Closing stale MCP connection [{0}] due to inactivity timeout", id);
            remove(id);
        }
    }
}