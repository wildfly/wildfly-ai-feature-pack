/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

/**
 * Listener for MCP message handling events.
 * <p>
 * Implementations can observe message dispatch lifecycle events for
 * instrumentation (e.g., OpenTelemetry metrics/tracing) or custom monitoring.
 * All methods have empty default implementations so listeners can override
 * only the events they care about.
 * </p>
 */
public interface MCPMessageListener {

    /**
     * Called before a JSON-RPC message is dispatched. Listeners may use
     * {@link MCPMessageContext#setAttribute} to store per-request state for
     * use in the corresponding {@link #onAfterMessageDispatched} or {@link #onError} callback.
     *
     * @param context the message context for this dispatch
     */
    default void onBeforeMessageDispatched(MCPMessageContext context) {
    }

    /**
     * Called after a JSON-RPC message has been successfully dispatched.
     * The context contains the elapsed duration; no error code is set.
     *
     * @param context the message context for this dispatch
     */
    default void onAfterMessageDispatched(MCPMessageContext context) {
    }

    /**
     * Called when message dispatch fails with an exception or when the handler sets an error
     * code on the context. The context contains the elapsed duration and, if set, an error
     * code and message.
     *
     * @param context the message context for this dispatch
     * @param error the exception that caused the failure, or {@code null} if the handler
     * set an error code without throwing
     */
    default void onError(MCPMessageContext context, Throwable error) {
    }

    /**
     * Called when a connection is removed from the manager, whether by an explicit {@code q/close}
     * message, an idle timeout, or server shutdown.Listeners should use this to release any
     * per-connection state (e.g. session start times) to prevent memory leaks.
     *
     * @param connectionId
     */
    default void onConnectionClosed(String connectionId) {
    }
}
