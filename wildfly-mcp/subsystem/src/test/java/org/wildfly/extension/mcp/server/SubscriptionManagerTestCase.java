/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class SubscriptionManagerTestCase {

    private SubscriptionManager manager;

    @Before
    public void setUp() {
        manager = new SubscriptionManager();
    }

    @Test
    public void testListenAddsSubscription() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://info")));

        assertTrue(manager.isSubscribed("conn-1", "resource", "test://info"));
        assertFalse(manager.isSubscribed("conn-1", "resource", "test://other"));
    }

    @Test
    public void testListenReplaces() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://first")));
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://second")));

        assertFalse("first should be gone", manager.isSubscribed("conn-1", "resource", "test://first"));
        assertTrue("second should be present", manager.isSubscribed("conn-1", "resource", "test://second"));
    }

    @Test
    public void testListenEmptyClears() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://info")));
        manager.listen("conn-1", Set.of());

        assertFalse(manager.isSubscribed("conn-1", "resource", "test://info"));
        assertEquals(0, manager.getSubscriptions("conn-1").size());
    }

    @Test
    public void testRemoveConnection() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://info")));

        manager.removeConnection("conn-1");

        assertFalse(manager.isSubscribed("conn-1", "resource", "test://info"));
    }

    @Test
    public void testIsolation() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://a")));
        manager.listen("conn-2", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://b")));

        assertTrue(manager.isSubscribed("conn-1", "resource", "test://a"));
        assertFalse(manager.isSubscribed("conn-1", "resource", "test://b"));
        assertTrue(manager.isSubscribed("conn-2", "resource", "test://b"));
        assertFalse(manager.isSubscribed("conn-2", "resource", "test://a"));
    }

    @Test
    public void testMultipleSubscriptions() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://a"),
                new SubscriptionManager.SubscriptionTarget("resource", "test://b")));

        assertTrue(manager.isSubscribed("conn-1", "resource", "test://a"));
        assertTrue(manager.isSubscribed("conn-1", "resource", "test://b"));
        assertEquals(2, manager.getSubscriptions("conn-1").size());
    }

    @Test
    public void testUnknownConnectionNotSubscribed() {
        assertFalse(manager.isSubscribed("unknown", "resource", "test://info"));
        assertEquals(0, manager.getSubscriptions("unknown").size());
    }

    @Test
    public void testForEachSubscriberMatchesResourceType() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://info")));

        List<String> notified = new ArrayList<>();
        manager.forEachSubscriber("resource", "test://info", notified::add);
        assertEquals("Should find subscriber with type 'resource'", 1, notified.size());
        assertEquals("conn-1", notified.get(0));
    }

    @Test
    public void testForEachSubscriberDoesNotMatchWrongType() {
        manager.listen("conn-1", Set.of(
                new SubscriptionManager.SubscriptionTarget("resource", "test://info")));

        List<String> notified = new ArrayList<>();
        manager.forEachSubscriber("resources", "test://info", notified::add);
        assertTrue("Type 'resources' should not match subscription with type 'resource'", notified.isEmpty());
    }
}
