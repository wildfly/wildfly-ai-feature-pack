/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.wildfly.extension.mcp.server.MCPTestHelpers.initializeMessage;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.ProtocolVersion;
import org.wildfly.extension.mcp.api.RequestMetadata;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class ServerDiscoverTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private ConnectionManager connectionManager;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("echo", MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "echo",
                new MethodMetadata("echo", "Echoes", null, null, List.of(),
                        "org.test.EchoTool", "java.lang.String")).build());

        connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");
        responder = new TestResponder();
    }

    @Test
    public void testDiscoverInNewState() {
        TestMCPConnection connection = new TestMCPConnection("conn-1");
        connectionManager.add(connection);
        assertTrue(connection.status() == MCPConnection.Status.NEW);

        handler.handle(jsonRpcRequest(1, "server/discover"), connection, responder);

        assertTrue("Should have a result", responder.hasResult());
        JsonObject result = responder.lastResult();
        assertSupportedVersions(result);
        assertNotNull("Should have capabilities", result.getJsonObject("capabilities"));
        assertNotNull("Should have serverInfo", result.getJsonObject("serverInfo"));
    }

    @Test
    public void testDiscoverInInitializingState() {
        TestMCPConnection connection = new TestMCPConnection("conn-2");
        connectionManager.add(connection);
        handler.handle(initializeMessage(1), connection, responder);
        assertTrue(connection.status() == MCPConnection.Status.INITIALIZING);
        responder.clear();

        handler.handle(jsonRpcRequest(2, "server/discover"), connection, responder);

        assertTrue("Should have a result", responder.hasResult());
        assertSupportedVersions(responder.lastResult());
    }

    @Test
    public void testDiscoverInOperationState() {
        TestMCPConnection connection = new TestMCPConnection("conn-3");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);

        handler.handle(jsonRpcRequest(3, "server/discover"), connection, responder);

        assertTrue("Should have a result", responder.hasResult());
        assertSupportedVersions(responder.lastResult());
    }

    @Test
    public void testDiscoverViaStatelessConnection() {
        RequestMetadata metadata = new RequestMetadata(
                ProtocolVersion.V_2026_07_28,
                List.of(new ClientCapability("elicitation", Set.of())),
                Map.of());
        StatelessConnection connection = new StatelessConnection(metadata);

        handler.handle(jsonRpcRequest(4, "server/discover"), connection, responder);

        assertTrue("Should have a result", responder.hasResult());
        assertSupportedVersions(responder.lastResult());
        assertNotNull(responder.lastResult().getJsonObject("capabilities"));
        assertNotNull(responder.lastResult().getJsonObject("serverInfo"));
    }

    @Test
    public void testDiscoverContainsServerInfo() {
        TestMCPConnection connection = new TestMCPConnection("conn-5");
        connectionManager.add(connection);

        handler.handle(jsonRpcRequest(5, "server/discover"), connection, responder);

        JsonObject serverInfo = responder.lastResult().getJsonObject("serverInfo");
        assertNotNull(serverInfo);
        assertTrue("Should contain server name", serverInfo.containsKey("name"));
        assertTrue("Should contain server version", serverInfo.containsKey("version"));
    }

    private void assertSupportedVersions(JsonObject result) {
        JsonArray versions = result.getJsonArray("supportedVersions");
        assertNotNull("Should have supportedVersions", versions);
        List<String> versionStrings = versions.getValuesAs(jakarta.json.JsonString::getString);
        assertTrue("Should contain legacy version",
                versionStrings.contains(ProtocolVersion.V_2025_11_25.wireValue()));
        assertTrue("Should contain modern version",
                versionStrings.contains(ProtocolVersion.V_2026_07_28.wireValue()));
    }
}
