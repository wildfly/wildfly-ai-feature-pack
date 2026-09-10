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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Responder;

public class ServerSentEventResponder implements Responder, MCPConnection {

    private final JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(Collections.emptyMap());
    private final ServerSentEventConnection connection;
    private final String id;
    private final AtomicInteger lastEventId = new AtomicInteger(-1);
    private final AtomicReference<Status> status;
    private final AtomicReference<InitializeRequest> initializeRequest;
    private final PendingRequestRegistry pendingRequestRegistry = new PendingRequestRegistry();
    private final List<ServerSentEventConnection> notificationStreams = new CopyOnWriteArrayList<>();
    private volatile long lastActivity;
    private final AtomicReference<Future<?>> future = new AtomicReference<>();
    // Set once the primary channel (the initialize POST's SSE stream) has been closed on purpose after
    // delivering the initialize response. The session itself stays alive; future pushes to clients that
    // haven't (yet) opened a notification stream via GET are simply not delivered over this channel.
    private final AtomicBoolean primaryChannelClosed = new AtomicBoolean(false);
    // Streamable HTTP (SEP-1699) upgrades the initialize POST into a throwaway SSE stream: once its response
    // is sent, the server closes it and expects the client to open a dedicated GET stream for further pushes.
    // The legacy HTTP+SSE transport (/sse) has no such reconnection mechanism — its one SSE connection, opened
    // on GET /sse, IS the client's only channel for the entire session, so it must never be auto-closed here.
    private final boolean closePrimaryChannelAfterInitializeResponse;

    ServerSentEventResponder(ServerSentEventConnection connection, String id, boolean closePrimaryChannelAfterInitializeResponse) {
        this.connection = connection;
        this.status = new AtomicReference<>(Status.NEW);
        this.initializeRequest = new AtomicReference<>();
        this.id = id;
        this.lastActivity = System.currentTimeMillis();
        this.closePrimaryChannelAfterInitializeResponse = closePrimaryChannelAfterInitializeResponse;
    }

    @Override
    public boolean initialize(InitializeRequest request) {
        if (status.compareAndSet(Status.NEW, Status.INITIALIZING)) {
            initializeRequest.set(request);
            this.lastActivity = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public boolean setInitialized() {
        boolean result = status.compareAndSet(Status.INITIALIZING, Status.IN_OPERATION);
        if (result) {
            this.lastActivity = System.currentTimeMillis();
        }
        return result;
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
        // The initialize response is the only message ever sent while INITIALIZING; once it's flushed, the
        // primary channel can close — later server-initiated pushes go out via a GET-established notification
        // stream (or, for pushes raised while handling a specific request, via that request's own stream).
        boolean isInitializeResponse = this.closePrimaryChannelAfterInitializeResponse && this.status.get() == Status.INITIALIZING;
        try (StringWriter writer = new StringWriter(); JsonWriter jsonWriter = jsonWriterFactory.createWriter(writer)) {
            jsonWriter.writeObject(message);
            sendInternal("message", writer.toString(), message.containsKey("method"), isInitializeResponse);
        } catch (IOException ex) {
            ROOT_LOGGER.failureSendingMessage(ex);
        }
    }

    @Override
    public long lastActivity() {
        return lastActivity;
    }

    @Override
    public void sendSync(JsonObject message) throws InterruptedException {
        try (StringWriter writer = new StringWriter(); JsonWriter jsonWriter = jsonWriterFactory.createWriter(writer)) {
            jsonWriter.writeObject(message);
            String data = writer.toString();
            String eventId = "" + lastEventId();

            // Collect every channel this session actually has open right now: the primary channel if it
            // hasn't been closed (e.g. a connection that never left NEW/INITIALIZING), plus any GET-established
            // notification streams. broadcastThenShutdown relies on this method only returning once delivery
            // has actually completed, since it tears down all connections immediately afterward.
            List<ServerSentEventConnection> targets = new ArrayList<>();
            if (!primaryChannelClosed.get()) {
                connection.getResponseHeaders().add(MCP_SESSION_ID_HEADER, id);
                targets.add(connection);
            }
            for (ServerSentEventConnection stream : notificationStreams) {
                if (stream.isOpen()) {
                    targets.add(stream);
                }
            }
            if (targets.isEmpty()) {
                return;
            }

            CountDownLatch latch = new CountDownLatch(targets.size());
            ServerSentEventConnection.EventCallback callback = new ServerSentEventConnection.EventCallback() {
                @Override
                public void done(ServerSentEventConnection connection, String data, String event, String id) {
                    ROOT_LOGGER.debugf("Message sent: %s", data);
                    latch.countDown();
                }

                @Override
                public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                    // Only ever called from broadcastThenShutdown, notifying every session right before tearing
                    // all of them down: a target that's already gone (client disconnected mid-session, typical
                    // for many short-lived clients) is expected and not actionable, so this is DEBUG rather than
                    // the WARN used for send()'s live-operation delivery failures.
                    ROOT_LOGGER.debugf("Failed to send event during shutdown broadcast: %s", data);
                    latch.countDown();
                }
            };
            for (ServerSentEventConnection target : targets) {
                target.send(data, "message", eventId, callback);
            }
            latch.await(5, TimeUnit.SECONDS);
        } catch (IOException ex) {
            throw ROOT_LOGGER.failedToSendSyncNotification(ex);
        }
    }

    void send(String name, String message) {
        sendInternal(name, message, true, false);
    }

    private void sendInternal(String name, String message, boolean broadcastToStreams, boolean closeAfterSend) {
        this.lastActivity = System.currentTimeMillis();
        ROOT_LOGGER.debugf("Sending message of type %s with content %s", name, message);
        String eventId = "" + lastEventId();
        if (!primaryChannelClosed.get()) {
            connection.getResponseHeaders().add(MCP_SESSION_ID_HEADER, id);
            connection.send(message, name, eventId, new ServerSentEventConnection.EventCallback() {
                @Override
                public void done(ServerSentEventConnection connection, String data, String event, String id) {
                    ROOT_LOGGER.debugf("Message sent: %s", data);
                    if (closeAfterSend) {
                        closePrimaryChannel();
                    }
                }

                @Override
                public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                    ROOT_LOGGER.failedToSendEvent(data);
                    close();
                }
            });
        }
        if (broadcastToStreams) {
            for (ServerSentEventConnection stream : notificationStreams) {
                if (stream.isOpen()) {
                    stream.send(message, name, eventId, new ServerSentEventConnection.EventCallback() {
                        @Override
                        public void done(ServerSentEventConnection c, String data, String event, String id) {
                        }

                        @Override
                        public void failed(ServerSentEventConnection c, String data, String event, String id, IOException e) {
                            ROOT_LOGGER.debugf("Failed to send to notification stream, removing");
                            notificationStreams.remove(stream);
                        }
                    });
                }
            }
        }
    }

    /**
     * Registers an additional, GET-established SSE stream used to deliver out-of-band server pushes
     * (list-changed notifications, resource updates) once the primary channel may have been closed.
     * Closing the stream does not close the session; clients may reconnect a listening stream using the
     * existing session ID until the session is explicitly terminated or expires through idle cleanup.
     */
    void addNotificationStream(ServerSentEventConnection stream) {
        notificationStreams.add(stream);
        stream.addCloseTask(channel -> notificationStreams.remove(stream));
    }

    /**
     * Closes just the primary channel (the initialize POST's SSE stream) once its response has been
     * delivered. The session itself is unaffected: {@link #addNotificationStream} connections, and
     * subsequent per-POST requests, remain fully functional.
     */
    private void closePrimaryChannel() {
        if (primaryChannelClosed.compareAndSet(false, true)) {
            try {
                connection.close();
            } catch (IOException ex) {
                ROOT_LOGGER.debug("Error closing the initial SSE channel after sending the initialize response", ex);
            }
        }
    }

    /**
     * @return {@code true} if the primary channel was closed intentionally by {@link #closePrimaryChannel()}
     * (as opposed to the client disconnecting), in which case the connection's close listener must not treat
     * it as the session ending.
     */
    boolean isPrimaryChannelClosedIntentionally() {
        return primaryChannelClosed.get();
    }

    @Override
    public void close() {
        for (ServerSentEventConnection stream : notificationStreams) {
            try {
                stream.close();
            } catch (IOException ex) {
                ROOT_LOGGER.debug("Error closing notification stream", ex);
            }
        }
        notificationStreams.clear();
        try {
            this.connection.close();
        } catch (IOException ex) {
            ROOT_LOGGER.debug("Error closing the SSEConnection", ex);
        }
    }

    @Override
    public void task(Future<?> future) {
        Future<?> previous = this.future.getAndSet(future);
        if (previous != null && !previous.isDone()) {
            ROOT_LOGGER.debug("Task not finished");
            previous.cancel(true);
        }
    }

    @Override
    public void cancel() {
        Future<?> currentFuture = this.future.getAndSet(null);
        if (currentFuture != null && !currentFuture.isDone()) {
            ROOT_LOGGER.debug("Task cancelled");
            currentFuture.cancel(true);
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
    public int lastEventId() {
        return lastEventId.incrementAndGet();
    }
}
