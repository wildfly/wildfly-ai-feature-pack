/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.server.PendingRequestRegistry;

public class ConnectionManagerTestCase {

    private ConnectionManager manager;
    private ScheduledExecutorService scheduler;

    @Before
    public void setUp() {
        manager = new ConnectionManager();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() {
        manager.stop();
        scheduler.shutdownNow();
    }

    // ==================== Basic add / get / remove ====================

    @Test
    public void testAddAndGet() {
        TestableConnection conn = new TestableConnection("conn-1");
        manager.add(conn);
        assertNotNull(manager.get("conn-1"));
    }

    @Test
    public void testGetUnknownIdReturnsNull() {
        assertNull(manager.get("nonexistent"));
    }

    @Test
    public void testRemoveClosesConnection() {
        TestableConnection conn = new TestableConnection("conn-1");
        manager.add(conn);

        boolean removed = manager.remove("conn-1");

        assertTrue(removed);
        assertTrue(conn.isClosed());
        assertNull(manager.get("conn-1"));
    }

    @Test
    public void testRemoveReturnsFalseForUnknownId() {
        assertFalse(manager.remove("nonexistent"));
    }

    // ==================== Cleanup / timeout ====================

    @Test
    public void testCleanupRemovesStaleConnection() {
        // lastActivity 1 hour in the past
        TestableConnection stale = new TestableConnection("stale");
        stale.setLastActivity(System.currentTimeMillis() - 3_600_000L);
        manager.add(stale);

        // 30-minute timeout — connection is 1 hour old, so it must be evicted
        manager.cleanup(1800L);

        assertNull(manager.get("stale"));
        assertTrue(stale.isClosed());
    }

    @Test
    public void testCleanupKeepsFreshConnection() {
        // lastActivity is right now (default)
        TestableConnection fresh = new TestableConnection("fresh");
        manager.add(fresh);

        manager.cleanup(1800L);

        assertNotNull(manager.get("fresh"));
        assertFalse(fresh.isClosed());
    }

    @Test
    public void testCleanupOnlyEvictsStaleConnections() {
        TestableConnection stale = new TestableConnection("stale");
        stale.setLastActivity(System.currentTimeMillis() - 3_600_000L);

        TestableConnection fresh = new TestableConnection("fresh");

        manager.add(stale);
        manager.add(fresh);

        manager.cleanup(1800L);

        assertNull(manager.get("stale"));
        assertTrue(stale.isClosed());
        assertNotNull(manager.get("fresh"));
        assertFalse(fresh.isClosed());
    }

    // ==================== Scheduler lifecycle ====================

    @Test
    public void testStartAndStop() {
        manager.start(1800L, scheduler);
        manager.stop();
        // stop() should be idempotent — second call must not throw
        manager.stop();
    }

    @Test
    public void testStopBeforeStartIsIdempotent() {
        // stop() without a preceding start() must not throw
        manager.stop();
    }

    @Test
    public void testShutdownClosesAllConnections() {
        TestableConnection conn1 = new TestableConnection("conn-1");
        TestableConnection conn2 = new TestableConnection("conn-2");
        manager.add(conn1);
        manager.add(conn2);
        manager.start(1800L, scheduler);

        manager.shutdown();

        assertTrue(conn1.isClosed());
        assertTrue(conn2.isClosed());
        assertNull(manager.get("conn-1"));
        assertNull(manager.get("conn-2"));
    }

    @Test
    public void testShutdownWithoutStartIsIdempotent() {
        manager.shutdown();
    }

    // ==================== Broadcast ====================

    @Test
    public void testBroadcastWithNoConnections() {
        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/prompts/list_changed")
                .build();
        manager.broadcast(notification);
    }

    @Test
    public void testBroadcastSendsToInOperationResponder() {
        TestableResponderConnection conn = new TestableResponderConnection("conn-1", MCPConnection.Status.IN_OPERATION);
        manager.add(conn);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/prompts/list_changed")
                .build();
        manager.broadcast(notification);

        assertEquals(1, conn.receivedMessages().size());
        assertEquals("notifications/prompts/list_changed", conn.receivedMessages().get(0).getString("method"));
    }

    @Test
    public void testBroadcastSkipsNonInOperationConnections() {
        TestableResponderConnection newConn = new TestableResponderConnection("new", MCPConnection.Status.NEW);
        TestableResponderConnection initConn = new TestableResponderConnection("init", MCPConnection.Status.INITIALIZING);
        TestableResponderConnection shutdownConn = new TestableResponderConnection("shutdown", MCPConnection.Status.SHUTDOWN);
        manager.add(newConn);
        manager.add(initConn);
        manager.add(shutdownConn);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/resources/list_changed")
                .build();
        manager.broadcast(notification);

        assertTrue(newConn.receivedMessages().isEmpty());
        assertTrue(initConn.receivedMessages().isEmpty());
        assertTrue(shutdownConn.receivedMessages().isEmpty());
    }

    @Test
    public void testBroadcastSkipsNonResponderConnections() {
        TestableConnection conn = new TestableConnection("conn-1");
        manager.add(conn);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/prompts/list_changed")
                .build();
        manager.broadcast(notification);
        // TestableConnection does not implement Responder — no exception, no delivery
    }

    @Test
    public void testBroadcastToMultipleActiveConnections() {
        TestableResponderConnection conn1 = new TestableResponderConnection("conn-1", MCPConnection.Status.IN_OPERATION);
        TestableResponderConnection conn2 = new TestableResponderConnection("conn-2", MCPConnection.Status.IN_OPERATION);
        manager.add(conn1);
        manager.add(conn2);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/prompts/list_changed")
                .build();
        manager.broadcast(notification);

        assertEquals(1, conn1.receivedMessages().size());
        assertEquals(1, conn2.receivedMessages().size());
    }

    @Test
    public void testBroadcastMixedConnections() {
        TestableResponderConnection active = new TestableResponderConnection("active", MCPConnection.Status.IN_OPERATION);
        TestableResponderConnection initializing = new TestableResponderConnection("initializing", MCPConnection.Status.INITIALIZING);
        TestableConnection plainConn = new TestableConnection("plain");
        manager.add(active);
        manager.add(initializing);
        manager.add(plainConn);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/prompts/list_changed")
                .build();
        manager.broadcast(notification);

        assertEquals(1, active.receivedMessages().size());
        assertTrue(initializing.receivedMessages().isEmpty());
    }

    // ==================== onConnectionClosed listener ====================

    @Test
    public void testListenerNotifiedOnRemove() {
        TrackingListener listener = new TrackingListener();
        manager.setListeners(List.of(listener));
        TestableConnection conn = new TestableConnection("conn-1");
        manager.add(conn);

        manager.remove("conn-1");

        assertEquals(List.of("conn-1"), listener.closedIds());
    }

    @Test
    public void testListenerNotifiedOnCleanup() {
        TrackingListener listener = new TrackingListener();
        manager.setListeners(List.of(listener));
        TestableConnection stale = new TestableConnection("stale");
        stale.setLastActivity(System.currentTimeMillis() - 3_600_000L);
        manager.add(stale);

        manager.cleanup(1800L);

        assertEquals(List.of("stale"), listener.closedIds());
    }

    @Test
    public void testListenerNotifiedOnShutdown() {
        TrackingListener listener = new TrackingListener();
        manager.setListeners(List.of(listener));
        TestableConnection conn1 = new TestableConnection("conn-1");
        TestableConnection conn2 = new TestableConnection("conn-2");
        manager.add(conn1);
        manager.add(conn2);

        manager.shutdown();

        assertEquals(2, listener.closedIds().size());
        assertTrue(listener.closedIds().contains("conn-1"));
        assertTrue(listener.closedIds().contains("conn-2"));
    }

    @Test
    public void testListenerNotNotifiedForUnknownId() {
        TrackingListener listener = new TrackingListener();
        manager.setListeners(List.of(listener));

        manager.remove("nonexistent");

        assertTrue(listener.closedIds().isEmpty());
    }

    @Test
    public void testThrowingListenerInOnConnectionClosedDoesNotBreakOtherListeners() {
        MCPMessageListener throwingListener = new MCPMessageListener() {
            @Override
            public void onConnectionClosed(String connectionId) {
                throw new RuntimeException("boom in onConnectionClosed");
            }
        };
        TrackingListener trackingListener = new TrackingListener();
        manager.setListeners(List.of(throwingListener, trackingListener));

        TestableConnection conn = new TestableConnection("conn-1");
        manager.add(conn);
        manager.remove("conn-1");

        assertEquals(List.of("conn-1"), trackingListener.closedIds());
    }

    // ==================== Test helper ====================

    private static class TestableConnection implements MCPConnection {

        private final String id;
        private boolean closed;
        private long lastActivity = System.currentTimeMillis();
        private final PendingRequestRegistry pendingRequestRegistry = new PendingRequestRegistry();

        TestableConnection(String id) {
            this.id = id;
        }

        void setLastActivity(long lastActivity) {
            this.lastActivity = lastActivity;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Status status() {
            return Status.NEW;
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
        public void task(Future future) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public PendingRequestRegistry pendingRequests() {
            return pendingRequestRegistry;
        }

        @Override
        public InitializeRequest initializeRequest() {
            return null;
        }

        @Override
        public long lastActivity() {
            return lastActivity;
        }
    }

    private static class TestableResponderConnection implements MCPConnection, Responder {

        private final String id;
        private final Status status;
        private final List<JsonObject> messages = new ArrayList<>();
        private final List<JsonObject> syncMessages = new ArrayList<>();
        private boolean closed = false;
        private int lastEventId = 0;
        private final PendingRequestRegistry pendingRequestRegistry = new PendingRequestRegistry();

        TestableResponderConnection(String id, Status status) {
            this.id = id;
            this.status = status;
        }

        List<JsonObject> receivedMessages() {
            return messages;
        }

        List<JsonObject> receivedSyncMessages() {
            return syncMessages;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public int lastEventId() {
            return lastEventId++;
        }

        @Override
        public void send(JsonObject message) {
            messages.add(message);
        }

        @Override
        public void sendSync(JsonObject message) {
            syncMessages.add(message);
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
            return false;
        }

        @Override
        public boolean setInitialized() {
            return false;
        }

        @Override
        public void task(Future future) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public PendingRequestRegistry pendingRequests() {
            return pendingRequestRegistry;
        }

        @Override
        public InitializeRequest initializeRequest() {
            return null;
        }

        @Override
        public long lastActivity() {
            return System.currentTimeMillis();
        }
    }

    // ==================== broadcastThenShutdown ====================

    @Test
    public void testBroadcastThenShutdownSendsToActiveConnections() {
        TestableResponderConnection conn1 = new TestableResponderConnection("conn-1", MCPConnection.Status.IN_OPERATION);
        TestableResponderConnection conn2 = new TestableResponderConnection("conn-2", MCPConnection.Status.IN_OPERATION);
        manager.add(conn1);
        manager.add(conn2);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/tools/list_changed")
                .build();
        manager.broadcastThenShutdown(notification);

        assertEquals(1, conn1.receivedSyncMessages().size());
        assertEquals("notifications/tools/list_changed", conn1.receivedSyncMessages().get(0).getString("method"));
        assertEquals(1, conn2.receivedSyncMessages().size());
        assertEquals("notifications/tools/list_changed", conn2.receivedSyncMessages().get(0).getString("method"));
    }

    @Test
    public void testBroadcastThenShutdownClosesAllConnections() {
        TestableResponderConnection conn1 = new TestableResponderConnection("conn-1", MCPConnection.Status.IN_OPERATION);
        TestableResponderConnection conn2 = new TestableResponderConnection("conn-2", MCPConnection.Status.INITIALIZING);
        manager.add(conn1);
        manager.add(conn2);
        manager.start(1800L, scheduler);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/tools/list_changed")
                .build();
        manager.broadcastThenShutdown(notification);

        assertTrue(conn1.isClosed());
        assertTrue(conn2.isClosed());
        assertNull(manager.get("conn-1"));
        assertNull(manager.get("conn-2"));
    }

    @Test
    public void testBroadcastThenShutdownSkipsNonActiveConnections() {
        TestableResponderConnection active = new TestableResponderConnection("active", MCPConnection.Status.IN_OPERATION);
        TestableResponderConnection initializing = new TestableResponderConnection("init", MCPConnection.Status.INITIALIZING);
        manager.add(active);
        manager.add(initializing);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/tools/list_changed")
                .build();
        manager.broadcastThenShutdown(notification);

        assertEquals(1, active.receivedSyncMessages().size());
        assertTrue(initializing.receivedSyncMessages().isEmpty());
        // Both connections are closed regardless of status
        assertTrue(active.isClosed());
        assertTrue(initializing.isClosed());
    }

    @Test
    public void testBroadcastThenShutdownStopsCleanupTask() {
        manager.start(1800L, scheduler);

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/tools/list_changed")
                .build();
        // Must not throw; stop() is called internally before broadcasting
        manager.broadcastThenShutdown(notification);

        // A second broadcastThenShutdown call must also not throw (cleanup task already stopped)
        manager.broadcastThenShutdown(notification);
    }

    @Test
    public void testBroadcastThenShutdownWithNoConnections() {
        manager.start(1800L, scheduler);
        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/tools/list_changed")
                .build();
        // Must not throw when there are no connections
        manager.broadcastThenShutdown(notification);
    }

    private static class TrackingListener implements MCPMessageListener {
        private final List<String> closedIds = new ArrayList<>();

        @Override
        public void onConnectionClosed(String connectionId) {
            closedIds.add(connectionId);
        }

        List<String> closedIds() {
            return closedIds;
        }
    }
}
