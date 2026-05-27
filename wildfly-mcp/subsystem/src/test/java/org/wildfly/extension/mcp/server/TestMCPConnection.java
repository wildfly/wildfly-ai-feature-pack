/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.concurrent.Future;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.server.PendingRequestRegistry;

public class TestMCPConnection implements MCPConnection {

    private final String id;
    private Status status = Status.NEW;
    private InitializeRequest initializeRequest;
    private boolean cancelled = false;
    private final PendingRequestRegistry pendingRequestRegistry = new PendingRequestRegistry();

    public TestMCPConnection(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public boolean initialize(InitializeRequest request) {
        if (status == Status.NEW) {
            this.initializeRequest = request;
            this.status = Status.INITIALIZING;
            return true;
        }
        return false;
    }

    @Override
    public boolean setInitialized() {
        if (status == Status.INITIALIZING) {
            this.status = Status.IN_OPERATION;
            return true;
        }
        return false;
    }

    @Override
    public void task(Future future) {
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public void close() {
        this.status = Status.SHUTDOWN;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public InitializeRequest getInitializeRequest() {
        return initializeRequest;
    }

    @Override
    public PendingRequestRegistry pendingRequests() {
        return pendingRequestRegistry;
    }

    @Override
    public InitializeRequest initializeRequest() {
        return initializeRequest;
    }

    @Override
    public long lastActivity() {
        return System.currentTimeMillis();
    }
}
