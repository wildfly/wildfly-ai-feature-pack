/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.Implementation;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.RequestMetadata;

public class StatelessConnection implements MCPConnection {

    static final String SYNTHETIC_CLIENT_NAME = "stateless-client";
    static final String SYNTHETIC_CLIENT_VERSION = "unknown";

    private final String id;
    private final RequestMetadata requestMetadata;
    private final InitializeRequest cachedInitializeRequest;
    private final PendingRequestRegistry pendingRequests = new PendingRequestRegistry();
    private volatile boolean cancelled;
    private volatile Future<?> runningTask;

    public StatelessConnection(RequestMetadata requestMetadata) {
        this.id = UUID.randomUUID().toString();
        this.requestMetadata = requestMetadata;
        if (requestMetadata != null) {
            List<ClientCapability> capabilities = requestMetadata.clientCapabilities() != null
                    ? requestMetadata.clientCapabilities()
                    : List.of();
            this.cachedInitializeRequest = new InitializeRequest(
                    new Implementation(SYNTHETIC_CLIENT_NAME, SYNTHETIC_CLIENT_VERSION),
                    requestMetadata.protocolVersion().wireValue(),
                    capabilities);
        } else {
            this.cachedInitializeRequest = null;
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Status status() {
        return Status.IN_OPERATION;
    }

    @Override
    public boolean initialize(InitializeRequest request) {
        return false;
    }

    @Override
    public boolean setInitialized() {
        return false;
    }

    @Override
    public void task(Future<?> future) {
        this.runningTask = future;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        Future<?> task = this.runningTask;
        if (task != null) {
            task.cancel(true);
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    RequestMetadata requestMetadata() {
        return requestMetadata;
    }

    @Override
    public void close() {
    }

    @Override
    public PendingRequestRegistry pendingRequests() {
        return pendingRequests;
    }

    @Override
    public InitializeRequest initializeRequest() {
        return cachedInitializeRequest;
    }

    @Override
    public long lastActivity() {
        return System.currentTimeMillis();
    }
}
