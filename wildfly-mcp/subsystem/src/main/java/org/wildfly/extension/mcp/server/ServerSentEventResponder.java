/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_SESSION_ID_HEADER;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.mcp_java.server.McpLog;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.MCPLogger;

public class ServerSentEventResponder implements Responder, MCPConnection {

    private final JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(Collections.emptyMap());
    private final ServerSentEventConnection connection;
    private final String id;
    private int lastEventId = -1;
    private final AtomicReference<Status> status;
    private final AtomicReference<InitializeRequest> initializeRequest;
    private final PendingRequestRegistry pendingRequestRegistry = new PendingRequestRegistry();
    private final AtomicReference<McpLog.LogLevel> logLevel = new AtomicReference<>(McpLog.LogLevel.NOTICE);
    private Future future;

    ServerSentEventResponder(ServerSentEventConnection connection, String id) {
        this.connection = connection;
        this.status = new AtomicReference<>(Status.NEW);
        this.initializeRequest = new AtomicReference<>();
        this.id = id;
    }

    @Override
    public boolean initialize(InitializeRequest request) {
        if (status.compareAndSet(Status.NEW, Status.INITIALIZING)) {
            initializeRequest.set(request);
            return true;
        }
        return false;
    }

    @Override
    public boolean setInitialized() {
        return status.compareAndSet(Status.INITIALIZING, Status.IN_OPERATION);
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public Status status() {
        return this.status.get();
    }

    @Override
    public void send(JsonObject message) {
        try (StringWriter writer = new StringWriter(); JsonWriter jsonWriter = jsonWriterFactory.createWriter(writer)) {
            jsonWriter.writeObject(message);
            send("message", writer.toString());
        } catch (IOException ex) {
            ROOT_LOGGER.failureSendingMessage(ex);
        }
    }

    public void send(String name, String message) {
        ROOT_LOGGER.debugf("Sending message of type %s with content %s", name, message);
        connection.getResponseHeaders().add(MCP_SESSION_ID_HEADER, id);
        connection.send(message, name,""+ lastEventId(), new ServerSentEventConnection.EventCallback() {
            @Override
            public void done(ServerSentEventConnection connection, String data, String event, String id) {
                ROOT_LOGGER.debugf("Message sent: %s", data);
            }

            @Override
            public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                ROOT_LOGGER.failedToSendEvent(data);
                close();
            }
        });
    }

    @Override
    public void close() {
        try {
            this.connection.close();
        } catch (IOException ex) {
            ROOT_LOGGER.debug("Error closing the SSEConnection", ex);
        }
    }

    @Override
    public void task(Future future) {
        if (this.future != null && !this.future.isDone()) {
            Future task = this.future;
            this.future = null;
            ROOT_LOGGER.debug("Task not finished");
            task.cancel(true);
        }
        this.future = future;
    }

    @Override
    public void cancel() {
        if (this.future != null && !this.future.isDone()) {
            Future task = this.future;
            this.future = null;
            ROOT_LOGGER.debug("Task cancelled");
            task.cancel(true);
        }

    }

    @Override
    public PendingRequestRegistry pendingRequests() {
        return pendingRequestRegistry;
    }

    @Override
    public InitializeRequest initializeRequest() {
        return initializeRequest.get();
    }

    @Override
    public McpLog.LogLevel logLevel() {
        return logLevel.get();
    }

    @Override
    public void setLogLevel(McpLog.LogLevel level) {
        logLevel.set(level);
    }

    @Override
    public int lastEventId() {
        return lastEventId++;
    }
}
