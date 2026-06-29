/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.wildfly.extension.mcp.api.Messages.newError;
import static org.wildfly.extension.mcp.api.Messages.newResult;

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Abstraction over a live client connection that can receive JSON-RPC messages.
 * <p>
 * Implementations wrap an SSE or HTTP response channel. The default {@code send*} convenience
 * methods delegate to {@link #send(JsonObject)} and cover the most common response shapes
 * (result, error, internal error). Override {@link #sendSync(JsonObject)} when the channel
 * must flush before the caller continues (e.g. during shutdown).
 * </p>
 */
public interface Responder {

    /**
     * Returns the SSE {@code Last-Event-ID} associated with this connection,
     * used to resume an interrupted stream.
     *
     * @return the last event ID, or 0 if no event has been sent yet
     */
    int lastEventId();

    /**
     * Sends a JSON-RPC message to the client.
     *
     * @param message the JSON-RPC message to send
     */
    void send(JsonObject message);

    /**
     * Sends a message and blocks until it has been flushed to the client, or the timeout elapses.
     * Used by {@link ConnectionManager#broadcastThenShutdown} to ensure notifications reach clients
     * before the connection is closed.
     *
     * @param message the JSON-RPC message to send
     * @throws InterruptedException if the thread is interrupted while waiting for the send to complete
     */
    default void sendSync(JsonObject message) throws InterruptedException {
        send(message);
    }

    /**
     * Sends a successful JSON-RPC result response.
     *
     * @param id the JSON-RPC request ID to correlate with the response
     * @param result the result payload
     */
    default void sendResult(String id, JsonObjectBuilder result) {
        send(newResult(id, result));
    }

    /**
     * Sends a JSON-RPC error response with the given error code and message.
     *
     * @param id the JSON-RPC request ID to correlate with the response
     * @param code the JSON-RPC error code (e.g. {@link JsonRPC#INTERNAL_ERROR})
     * @param message a human-readable description of the error
     */
    default void sendError(String id, int code, String message) {
        send(newError(id, code,message));
    }
}
