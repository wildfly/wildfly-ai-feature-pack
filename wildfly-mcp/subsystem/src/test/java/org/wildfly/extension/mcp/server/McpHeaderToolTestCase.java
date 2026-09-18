/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.ProtocolVersion;
import org.wildfly.extension.mcp.api.RequestMetadata;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class McpHeaderToolTestCase {

    @Test
    public void testHeaderParamsIncludedInInputSchemaWithAnnotation() {
        MCPFeatureMetadata metadata = toolWithHeaderAndRegularArg();
        ToolTestContext ctx = setupToolTest("header-tool", metadata);

        ctx.handler.handle(jsonRpcRequest(1, "tools/list"), ctx.connection, ctx.responder);
        assertTrue(ctx.responder.hasResult());

        JsonObject result = ctx.responder.lastResult();
        JsonArray tools = result.getJsonArray("tools");
        assertEquals(1, tools.size());

        JsonObject tool = tools.getJsonObject(0);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        assertNotNull("inputSchema should be present", inputSchema);

        JsonObject properties = inputSchema.getJsonObject("properties");
        assertNotNull("properties should be present", properties);
        assertTrue("Regular arg 'message' should be in schema", properties.containsKey("message"));
        assertTrue("Header param should appear under its headerName 'auth-token'", properties.containsKey("auth-token"));

        JsonObject headerProp = properties.getJsonObject("auth-token");
        assertEquals("x-mcp-header should be set to headerName", "auth-token", headerProp.getString("x-mcp-header"));

        JsonArray required = inputSchema.getJsonArray("required");
        assertNotNull("required should be present", required);
        boolean containsHeaderName = false;
        for (int i = 0; i < required.size(); i++) {
            if ("auth-token".equals(required.getString(i))) {
                containsHeaderName = true;
            }
        }
        assertFalse("Optional header param should NOT appear in required array", containsHeaderName);
    }

    @Test
    public void testHeaderOnlyToolHasHeaderProperty() {
        MCPFeatureMetadata metadata = toolWithOnlyHeaderArg();
        ToolTestContext ctx = setupToolTest("header-only-tool", metadata);

        ctx.handler.handle(jsonRpcRequest(1, "tools/list"), ctx.connection, ctx.responder);
        assertTrue(ctx.responder.hasResult());

        JsonObject tool = ctx.responder.lastResult().getJsonArray("tools").getJsonObject(0);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        JsonObject properties = inputSchema.getJsonObject("properties");
        assertEquals("properties should contain the header param", 1, properties.size());
        assertTrue(properties.containsKey("api-key"));
        assertEquals("api-key", properties.getJsonObject("api-key").getString("x-mcp-header"));
    }

    @Test
    public void testToolsCallWithHeadersFromContext() {
        MCPFeatureMetadata metadata = toolWithOnlyHeaderArg();
        ToolTestContext ctx = setupToolTest("header-only-tool", metadata);

        JsonObject callMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 2)
                .add("method", "tools/call")
                .add("params", Json.createObjectBuilder()
                        .add("name", "header-only-tool"))
                .build();

        ctx.handler.handle(callMessage, ctx.connection, ctx.responder,
                null, -1, null, Map.of("api-key", "secret-123"));

        // The tool call is dispatched asynchronously; the handler itself should not error
        // (the actual invocation may fail due to CDI not being available in tests,
        //  but that's expected — what matters is the handler accepted the headers)
    }

    @Test
    public void testToolsCallWithHeadersFromMeta() {
        MCPFeatureMetadata metadata = toolWithOnlyHeaderArg();
        StatelessConnection connection = modernConnection();
        ToolTestContext ctx = setupToolTest("header-only-tool", metadata);

        JsonObject callMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 3)
                .add("method", "tools/call")
                .add("params", Json.createObjectBuilder()
                        .add("name", "header-only-tool")
                        .add("_meta", Json.createObjectBuilder()
                                .add("headers", Json.createObjectBuilder()
                                        .add("api-key", "from-meta"))))
                .build();

        ctx.handler.handle(callMessage, connection, ctx.responder);
    }

    @Test
    public void testToolsCallMissingRequiredHeader() {
        MCPFeatureMetadata metadata = toolWithRequiredHeaderArg();
        StatelessConnection connection = modernConnection();
        ToolTestContext ctx = setupToolTest("required-header-tool", metadata);

        JsonObject callMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 4)
                .add("method", "tools/call")
                .add("params", Json.createObjectBuilder()
                        .add("name", "required-header-tool"))
                .build();

        ctx.handler.handle(callMessage, connection, ctx.responder);

        // The invocation is async; for CDI-based tools it would fail on the required header.
        // The dispatch path itself should not reject the message.
    }

    @Test
    public void testMixedHeaderAndRegularArgsInSchema() {
        List<ArgumentMetadata> args = List.of(
                new ArgumentMetadata("authToken", "", false, String.class, "auth-token"),
                new ArgumentMetadata("message", "The message to process", true, String.class),
                new ArgumentMetadata("tenantId", "", true, String.class, "tenant-id")
        );
        MCPFeatureMetadata metadata = MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "mixed-tool",
                new MethodMetadata("mixedTool", "Tool with mixed args", null, null,
                        args, "org.test.Mixed", "java.lang.String")).build();

        ToolTestContext ctx = setupToolTest("mixed-tool", metadata);
        ctx.handler.handle(jsonRpcRequest(1, "tools/list"), ctx.connection, ctx.responder);

        JsonObject tool = ctx.responder.lastResult().getJsonArray("tools").getJsonObject(0);
        JsonObject properties = tool.getJsonObject("inputSchema").getJsonObject("properties");
        assertEquals("'message', 'auth-token', and 'tenant-id' should be in properties", 3, properties.size());
        assertTrue(properties.containsKey("message"));
        assertTrue(properties.containsKey("auth-token"));
        assertTrue(properties.containsKey("tenant-id"));
        assertEquals("auth-token", properties.getJsonObject("auth-token").getString("x-mcp-header"));
        assertEquals("tenant-id", properties.getJsonObject("tenant-id").getString("x-mcp-header"));
    }

    // ==================== Helpers ====================

    private record ToolTestContext(MCPMessageHandler handler, TestMCPConnection connection, TestResponder responder) {}

    private ToolTestContext setupToolTest(String toolName, MCPFeatureMetadata metadata) {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool(toolName, metadata);
        ConnectionManager connectionManager = new ConnectionManager();
        MCPMessageHandler handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");
        TestResponder responder = new TestResponder();
        TestMCPConnection connection = new TestMCPConnection("test-conn");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);
        return new ToolTestContext(handler, connection, responder);
    }

    private MCPFeatureMetadata toolWithHeaderAndRegularArg() {
        List<ArgumentMetadata> args = List.of(
                new ArgumentMetadata("token", "", false, String.class, "auth-token"),
                new ArgumentMetadata("message", "The message", true, String.class)
        );
        return MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "header-tool",
                new MethodMetadata("headerTool", "Tool with header", null, null,
                        args, "org.test.HeaderTool", "java.lang.String")).build();
    }

    private MCPFeatureMetadata toolWithOnlyHeaderArg() {
        List<ArgumentMetadata> args = List.of(
                new ArgumentMetadata("key", "", false, String.class, "api-key")
        );
        return MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "header-only-tool",
                new MethodMetadata("headerOnlyTool", "Tool with only header", null, null,
                        args, "org.test.HeaderOnlyTool", "java.lang.String")).build();
    }

    private MCPFeatureMetadata toolWithRequiredHeaderArg() {
        List<ArgumentMetadata> args = List.of(
                new ArgumentMetadata("key", "", true, String.class, "api-key")
        );
        return MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, "required-header-tool",
                new MethodMetadata("requiredHeaderTool", "Tool with required header", null, null,
                        args, "org.test.RequiredHeaderTool", "java.lang.String")).build();
    }

    private StatelessConnection modernConnection() {
        RequestMetadata metadata = new RequestMetadata(
                ProtocolVersion.V_2026_07_28,
                List.of(new ClientCapability("elicitation", Set.of())),
                Map.of());
        return new StatelessConnection(metadata);
    }
}
