/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}