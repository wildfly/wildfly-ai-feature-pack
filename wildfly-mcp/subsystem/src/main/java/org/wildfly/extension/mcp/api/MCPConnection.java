/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;
import java.io.Closeable;
import java.util.concurrent.Future;
import org.wildfly.extension.mcp.server.PendingRequestRegistry;

public interface MCPConnection extends Closeable {

    /**
     * See <a href="https://spec.modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle/">Lifecycle</a>
     */
    enum Status {
        NEW,
        INITIALIZING,
        IN_OPERATION,
        SHUTDOWN
    }

    String id();

    Status status();

    boolean initialize(InitializeRequest request);

    boolean setInitialized();

    void task(Future future);

    void cancel();

    PendingRequestRegistry pendingRequests();

    InitializeRequest initializeRequest();

    long lastActivity();
}
