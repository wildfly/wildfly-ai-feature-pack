/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.util.Map;

/**
 * Read-only context passed to {@link MCPMessageListener} callbacks.
 * Captures the JSON-RPC method name, connection identity, connection status,
 * and timing information for a single message dispatch.
 * <p>
 * The {@link #setAttribute} / {@link #getAttribute} methods allow listeners to stash
 * per-request state (e.g., an OpenTelemetry Span reference) between {@code onBeforeMessageDispatched}
 * and {@code onAfterMessageDispatched}/{@code onError}.
 * </p>
 */
public interface MCPMessageContext {

    String method();

    String connectionId();

    String requestId();

    String connectionStatus();

    long startTimeNanos();

    long durationNanos();

    int errorCode();

    boolean hasError();

    String errorMessage();

    Map<String, String> propagationHeaders();

    String genAiTarget();

    String protocolVersion();

    String clientAddress();

    int clientPort();

    String networkProtocolVersion();

    String resourceUri();

    <T> void setAttribute(MCPContextKey<T> key, T value);

    <T> T getAttribute(MCPContextKey<T> key);
}
