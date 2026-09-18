/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.ProtocolVersion;
import org.wildfly.extension.mcp.api.RequestMetadata;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class SubscriptionsListenTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private ConnectionManager connectionManager;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addResource("test://info", MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.RESOURCE, "test-info",
                new MethodMetadata("info", "Test info", "test://info", "text/plain",
                        List.of(), "org.test.InfoResource", "java.lang.String")).build());
        connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");
        responder = new TestResponder();
    }

    @Test
    public void testSubscriptionsListenOnStatelessConnectionSucceedsWithoutPersisting() {
        StatelessConnection connection = modernStatelessConnection();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder()
                        .add("subscriptions", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("type", "resource")
                                        .add("uri", "test://info"))))
                .build();

        handler.handle(message, connection, responder);

        List<JsonObject> messages = responder.allMessages();
        assertTrue("Should have at least 2 messages (acknowledgment + result)", messages.size() >= 2);

        JsonObject ack = messages.get(0);
        assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));

        JsonObject result = messages.get(1).getJsonObject("result");
        assertNotNull("Result should exist", result);
        assertTrue("Result should include subscriptionId", result.containsKey("subscriptionId"));

        assertFalse("Stateless subscription should not be persisted",
                handler.getSubscriptionManager().isSubscribed(connection.id(), "resource", "test://info"));
    }

    @Test
    public void testSubscriptionsListenWithStatefulConnection() {
        TestMCPConnection connection = new TestMCPConnection("stateful");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 2)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder()
                        .add("subscriptions", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("type", "resource")
                                        .add("uri", "test://info"))))
                .build();

        handler.handle(message, connection, responder);

        List<JsonObject> messages = responder.allMessages();
        assertTrue("Should have at least 2 messages (acknowledgment + result)", messages.size() >= 2);

        JsonObject ack = messages.get(0);
        assertEquals("notifications/subscriptions/acknowledged", ack.getString("method"));

        JsonObject result = messages.get(1).getJsonObject("result");
        assertNotNull("Result should exist", result);
        assertTrue("Result should include subscriptionId", result.containsKey("subscriptionId"));
        String subscriptionId = result.getString("subscriptionId");
        assertNotNull("subscriptionId should not be null", subscriptionId);
        assertFalse("subscriptionId should not be empty", subscriptionId.isEmpty());
        assertFalse("Notification should not have id", ack.containsKey("id"));
        JsonObject ackParams = ack.getJsonObject("params");
        assertNotNull("Acknowledgment should have params", ackParams);
        JsonObject ackMeta = ackParams.getJsonObject("_meta");
        assertNotNull("Acknowledgment should have _meta", ackMeta);
        assertEquals("subscriptionId should match",
                subscriptionId, ackMeta.getString("io.modelcontextprotocol/subscriptionId"));
    }

    @Test
    public void testSubscriptionsListenWithLegacyConnection() {
        TestMCPConnection connection = new TestMCPConnection("legacy");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 3)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder()
                        .add("subscriptions", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("type", "resource")
                                        .add("uri", "test://info"))))
                .build();

        handler.handle(message, connection, responder);
        assertTrue("Should succeed for legacy too", responder.hasResult());
    }

    @Test
    public void testSubscriptionsListenEmptyClears() {
        TestMCPConnection connection = new TestMCPConnection("stateful-empty");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        JsonObject listen = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 4)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder()
                        .add("subscriptions", Json.createArrayBuilder()))
                .build();

        handler.handle(listen, connection, responder);
        assertTrue("Should succeed with empty list", responder.hasResult());
    }

    @Test
    public void testSubscriptionsListenMissingParams() {
        TestMCPConnection connection = new TestMCPConnection("stateful-noparams");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 5)
                .add("method", "subscriptions/listen")
                .build();

        handler.handle(message, connection, responder);
        assertTrue("Should return error", responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testSubscriptionsListenMissingField() {
        TestMCPConnection connection = new TestMCPConnection("stateful-nofield");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 6)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder())
                .build();

        handler.handle(message, connection, responder);
        assertTrue("Should return error", responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testSubscriptionsListenReplacesExisting() {
        TestMCPConnection connection = new TestMCPConnection("stateful-replace");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        JsonObject first = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 7)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder()
                        .add("subscriptions", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("type", "resource")
                                        .add("uri", "test://first"))))
                .build();
        handler.handle(first, connection, responder);
        assertTrue(responder.hasResult());

        responder.clear();

        JsonObject second = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 8)
                .add("method", "subscriptions/listen")
                .add("params", Json.createObjectBuilder()
                        .add("subscriptions", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("type", "resource")
                                        .add("uri", "test://second"))))
                .build();
        handler.handle(second, connection, responder);
        assertTrue(responder.hasResult());
    }

    private StatelessConnection modernStatelessConnection() {
        RequestMetadata metadata = new RequestMetadata(
                ProtocolVersion.V_2026_07_28,
                List.of(new ClientCapability("elicitation", Set.of())),
                Map.of());
        return new StatelessConnection(metadata);
    }
}
