/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
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
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class StatelessMCPMessageHandlerTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private StatelessConnection statelessConnection;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();

        registry.addTool("echo", MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "echo",
                new MethodMetadata("echo", "Echoes the input", null, null,
                        List.of(new ArgumentMetadata("message", "The message to echo", true, String.class)),
                        "org.test.EchoTool", "java.lang.String")).build());

        ConnectionManager connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");

        responder = new TestResponder();
        RequestMetadata metadata = new RequestMetadata(
                ProtocolVersion.V_2026_07_28,
                List.of(new ClientCapability("elicitation", Set.of())),
                Map.of());
        statelessConnection = new StatelessConnection(metadata);
    }

    @Test
    public void testModernClientToolsListSkipsInitialize() {
        assertEquals(MCPConnection.Status.IN_OPERATION, statelessConnection.status());

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "tools/list")
                .build();

        handler.handle(message, statelessConnection, responder);

        assertTrue("Should have a result, not an error", responder.hasResult());
        JsonArray tools = responder.lastResult().getJsonArray("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertEquals("echo", tools.getJsonObject(0).getString("name"));
    }

    @Test
    public void testModernClientPromptsList() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 2)
                .add("method", "prompts/list")
                .build();

        handler.handle(message, statelessConnection, responder);

        assertTrue(responder.hasResult());
        assertNotNull(responder.lastResult().getJsonArray("prompts"));
    }

    @Test
    public void testModernClientUnsupportedMethodReturnsError() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 3)
                .add("method", "unsupported/method")
                .build();

        handler.handle(message, statelessConnection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32601, responder.lastError().getInt("code"));
    }

    @Test
    public void testUnsupportedProtocolVersionReturns32022() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 4)
                .add("method", "tools/list")
                .add("params", Json.createObjectBuilder()
                        .add("_meta", Json.createObjectBuilder()
                                .add("io.modelcontextprotocol/protocolVersion", "9999-01-01")
                                .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder())))
                .build();

        handler.handle(message, statelessConnection, responder);

        assertTrue("Should return an error", responder.hasError());
        assertEquals(-32022, responder.lastError().getInt("code"));
        assertTrue(responder.lastError().getString("message").contains("9999-01-01"));
    }

    @Test
    public void testMissingMetaFieldReturnsInvalidParams() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 5)
                .add("method", "tools/list")
                .add("params", Json.createObjectBuilder()
                        .add("_meta", Json.createObjectBuilder()
                                .add("io.modelcontextprotocol/protocolVersion", "2026-07-28")))
                .build();

        handler.handle(message, statelessConnection, responder);

        assertTrue("Should return an error for missing capabilities", responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }
}
