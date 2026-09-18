/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

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
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

/**
 * Tests that methods removed in the 2026-07-28 protocol version return METHOD_NOT_FOUND
 * when called by a modern client, while remaining functional for legacy clients.
 */
public class RemovedMethodsTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private StatelessConnection modernConnection;
    private TestMCPConnection legacyConnection;
    private ConnectionManager connectionManager;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("echo", MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "echo",
                new MethodMetadata("echo", "Echoes", null, null,
                        List.of(new ArgumentMetadata("message", "msg", true, String.class)),
                        "org.test.EchoTool", "java.lang.String")).build());
        registry.addResource("test://info", MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.RESOURCE, "test-info",
                new MethodMetadata("info", "Test info", "test://info", "text/plain",
                        List.of(), "org.test.InfoResource", "java.lang.String")).build());

        connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");
        responder = new TestResponder();

        RequestMetadata metadata = new RequestMetadata(
                ProtocolVersion.V_2026_07_28,
                List.of(new ClientCapability("elicitation", Set.of())),
                Map.of());
        modernConnection = new StatelessConnection(metadata);

        legacyConnection = new TestMCPConnection("legacy-conn");
        connectionManager.add(legacyConnection);
        MCPTestHelpers.moveToOperation(handler, legacyConnection, responder);
    }

    // ==================== Modern client: removed methods return -32601 ====================

    @Test
    public void testPingRemovedForModernClient() {
        handler.handle(jsonRpcRequest(1, "ping"), modernConnection, responder);
        assertMethodNotFound();
    }

    @Test
    public void testInitializeRemovedForModernClient() {
        handler.handle(jsonRpcRequest(2, "initialize"), modernConnection, responder);
        assertMethodNotFound();
    }

    @Test
    public void testNotificationsInitializedRemovedForModernClient() {
        handler.handle(jsonRpcRequest(3, "notifications/initialized"), modernConnection, responder);
        assertMethodNotFound();
    }

    @Test
    public void testLoggingSetLevelRemovedForModernClient() {
        handler.handle(jsonRpcRequest(4, "logging/setLevel"), modernConnection, responder);
        assertMethodNotFound();
    }

    @Test
    public void testNotificationsRootsListChangedRemovedForModernClient() {
        handler.handle(jsonRpcRequest(5, "notifications/roots/list_changed"), modernConnection, responder);
        assertMethodNotFound();
    }

    @Test
    public void testResourcesSubscribeRemovedForModernClient() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 6)
                .add("method", "resources/subscribe")
                .add("params", Json.createObjectBuilder().add("uri", "test://info"))
                .build();
        handler.handle(message, modernConnection, responder);
        assertMethodNotFound();
    }

    @Test
    public void testResourcesUnsubscribeRemovedForModernClient() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 7)
                .add("method", "resources/unsubscribe")
                .add("params", Json.createObjectBuilder().add("uri", "test://info"))
                .build();
        handler.handle(message, modernConnection, responder);
        assertMethodNotFound();
    }

    // ==================== Modern client: non-removed methods still work ====================

    @Test
    public void testToolsListWorksForModernClient() {
        handler.handle(jsonRpcRequest(8, "tools/list"), modernConnection, responder);
        assertTrue("tools/list should work for modern client", responder.hasResult());
    }

    @Test
    public void testServerDiscoverWorksForModernClient() {
        handler.handle(jsonRpcRequest(9, "server/discover"), modernConnection, responder);
        assertTrue("server/discover should work for modern client", responder.hasResult());
    }

    // ==================== Legacy client: removed methods still work ====================

    @Test
    public void testPingWorksForLegacyClient() {
        handler.handle(jsonRpcRequest(10, "ping"), legacyConnection, responder);
        assertTrue("ping should work for legacy client", responder.hasResult());
    }

    @Test
    public void testResourcesSubscribeWorksForLegacyClient() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 11)
                .add("method", "resources/subscribe")
                .add("params", Json.createObjectBuilder().add("uri", "test://info"))
                .build();
        handler.handle(message, legacyConnection, responder);
        assertTrue("resources/subscribe should work for legacy client", responder.hasResult());
    }

    private void assertMethodNotFound() {
        assertTrue("Should have error", responder.hasError());
        assertEquals(-32601, responder.lastError().getInt("code"));
    }
}
