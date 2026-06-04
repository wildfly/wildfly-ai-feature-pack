/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.awaitResult;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.Test;
import org.mcp_java.model.tool.ToolAnnotations;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;
import org.wildfly.extension.mcp.injection.tool.ToolSchemaGenerator;

public class ToolAnnotationsTestCase {

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

    @Test
    public void testDefaultHintsOmitsAnnotationsAndTitle() {
        JsonObject tool = listSingleTool(null, false);

        assertFalse("title should be absent with null annotations", tool.containsKey("title"));
        assertFalse("annotations should be absent when null", tool.containsKey("annotations"));
        assertFalse("outputSchema should be absent when structuredContent is false", tool.containsKey("outputSchema"));
    }

    @Test
    public void testTitleEmittedWhenNonEmpty() {
        ToolAnnotations annotations = new ToolAnnotations("My Tool Title", null, null, null, null);
        JsonObject tool = listSingleTool(annotations, false);

        assertTrue(tool.containsKey("title"));
        assertEquals("My Tool Title", tool.getString("title"));
    }

    @Test
    public void testTitleOmittedWhenEmpty() {
        ToolAnnotations annotations = new ToolAnnotations("", null, null, null, null);
        JsonObject tool = listSingleTool(annotations, false);

        assertFalse(tool.containsKey("title"));
    }

    @Test
    public void testReadOnlyHintEmitted() {
        assertSingleHintEmitted(new ToolAnnotations(null, true, null, null, null), "readOnlyHint", true);
    }

    @Test
    public void testDestructiveHintFalseEmitted() {
        assertSingleHintEmitted(new ToolAnnotations(null, null, false, null, null), "destructiveHint", false);
    }

    @Test
    public void testIdempotentHintEmitted() {
        assertSingleHintEmitted(new ToolAnnotations(null, null, null, true, null), "idempotentHint", true);
    }

    @Test
    public void testOpenWorldHintFalseEmitted() {
        assertSingleHintEmitted(new ToolAnnotations(null, null, null, null, false), "openWorldHint", false);
    }

    @Test
    public void testMultipleNonDefaultAnnotations() {
        ToolAnnotations annotations = new ToolAnnotations(null, true, false, true, false);
        JsonObject tool = listSingleTool(annotations, false);

        JsonObject ann = tool.getJsonObject("annotations");
        assertNotNull(ann);
        assertTrue(ann.getBoolean("readOnlyHint"));
        assertFalse(ann.getBoolean("destructiveHint"));
        assertTrue(ann.getBoolean("idempotentHint"));
        assertFalse(ann.getBoolean("openWorldHint"));
    }

    @Test
    public void testOutputSchemaEmittedWhenStructuredContent() {
        JsonObject tool = listSingleTool(null, true);

        assertTrue("outputSchema should be present when structuredContent is true", tool.containsKey("outputSchema"));
        JsonObject outputSchema = tool.getJsonObject("outputSchema");
        assertNotNull(outputSchema);
        assertEquals("string", outputSchema.getString("type"));
        assertFalse("outputSchema should not contain $schema field", outputSchema.containsKey("$schema"));
    }

    @Test
    public void testOutputSchemaOmittedWhenNotStructuredContent() {
        JsonObject tool = listSingleTool(null, false);

        assertFalse(tool.containsKey("outputSchema"));
    }

    @Test
    public void testTitleAndAnnotationsAndOutputSchemaTogether() {
        ToolAnnotations annotations = new ToolAnnotations("Full Tool", true, false, true, false);
        JsonObject tool = listSingleTool(annotations, true);

        assertEquals("Full Tool", tool.getString("title"));

        JsonObject ann = tool.getJsonObject("annotations");
        assertNotNull(ann);
        assertTrue(ann.getBoolean("readOnlyHint"));
        assertFalse(ann.getBoolean("destructiveHint"));
        assertTrue(ann.getBoolean("idempotentHint"));
        assertFalse(ann.getBoolean("openWorldHint"));

        assertNotNull(tool.getJsonObject("outputSchema"));
    }

    // ==================== toolsCall isError Tests ====================

    @Test
    public void testToolsCallSetsIsErrorOnException() throws Exception {
        ToolTestContext ctx = setupToolTest("broken-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "broken-tool",
                new MethodMetadata("brokenMethod", "A broken tool", null, null,
                        List.of(),
                        "org.nonexistent.DoesNotExist", "void")));

        JsonObject callMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 10)
                .add("method", "tools/call")
                .add("params", Json.createObjectBuilder()
                        .add("name", "broken-tool"))
                .build();
        ctx.handler().handle(callMessage, ctx.connection(), ctx.responder());

        assertTrue(awaitResult(ctx.responder(), 2000));
        JsonObject result = ctx.responder().lastResult();
        assertNotNull("Should return a result, not a JSON-RPC error", result);
        assertTrue("isError should be true", result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertNotNull(content);
        assertEquals(1, content.size());
        assertEquals("text", content.getJsonObject(0).getString("type"));
        assertFalse(content.getJsonObject(0).getString("text").isEmpty());
    }

    @Test
    public void testToolsCallIsErrorContentContainsGenericMessage() throws Exception {
        ToolTestContext ctx = setupToolTest("missing-class-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "missing-class-tool",
                new MethodMetadata("someMethod", "Tool with missing class", null, null,
                        List.of(),
                        "org.nonexistent.SomeClass", "void")));

        JsonObject callMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 11)
                .add("method", "tools/call")
                .add("params", Json.createObjectBuilder()
                        .add("name", "missing-class-tool"))
                .build();
        ctx.handler().handle(callMessage, ctx.connection(), ctx.responder());

        assertTrue(awaitResult(ctx.responder(), 2000));
        JsonObject result = ctx.responder().lastResult();
        assertTrue(result.getBoolean("isError"));
        String errorText = result.getJsonArray("content").getJsonObject(0).getString("text");
        assertFalse("Error text should be non-empty", errorText.isEmpty());
    }

    @Test
    public void testOutputSchemaOmittedWhenReturnTypeUnloadable() {
        ToolTestContext ctx = setupToolTest("unloadable-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "unloadable-tool",
                new MethodMetadata("method", "Tool with unloadable return type", null, null,
                        List.of(),
                        "org.test.TestTool", "org.nonexistent.NoSuchType"),
                null, true, "", "", ""));

        ctx.handler().handle(jsonRpcRequest(3, "tools/list"), ctx.connection(), ctx.responder());
        assertTrue(ctx.responder().hasResult());

        JsonArray tools = ctx.responder().lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        JsonObject tool = tools.getJsonObject(0);
        assertFalse("outputSchema should be absent when return type cannot be loaded", tool.containsKey("outputSchema"));
    }

    // ==================== Schema Generator Tests ====================

    public static class TestInputSchemaGenerator implements ToolSchemaGenerator {
        @Override
        public String generate() {
            return "{\"type\":\"object\",\"properties\":{\"customArg\":{\"type\":\"integer\",\"description\":\"A custom argument\"}},\"required\":[\"customArg\"]}";
        }
    }

    public static class TestOutputSchemaGenerator implements ToolSchemaGenerator {
        @Override
        public String generate() {
            return "{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"number\"}}}";
        }
    }

    @Test
    public void testInputSchemaFromGenerator() {
        ToolTestContext ctx = setupToolTest("gen-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "gen-tool",
                new MethodMetadata("genTool", "Tool with generator", null, null,
                        List.of(new ArgumentMetadata("ignored", "This is ignored", true, String.class)),
                        "org.test.TestTool", "java.lang.String"),
                null, false,
                TestInputSchemaGenerator.class.getName(), "", ""));

        ctx.handler().handle(jsonRpcRequest(3, "tools/list"), ctx.connection(), ctx.responder());
        assertTrue(ctx.responder().hasResult());

        JsonArray tools = ctx.responder().lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        JsonObject tool = tools.getJsonObject(0);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        assertNotNull(inputSchema);
        assertEquals("object", inputSchema.getString("type"));
        assertNotNull(inputSchema.getJsonObject("properties").getJsonObject("customArg"));
        assertEquals("integer", inputSchema.getJsonObject("properties").getJsonObject("customArg").getString("type"));
    }

    @Test
    public void testOutputSchemaFromGenerator() {
        ToolTestContext ctx = setupToolTest("gen-out-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "gen-out-tool",
                new MethodMetadata("genOutTool", "Tool with output generator", null, null,
                        List.of(), "org.test.TestTool", "java.lang.String"),
                null, true, "",
                TestOutputSchemaGenerator.class.getName(), ""));

        ctx.handler().handle(jsonRpcRequest(3, "tools/list"), ctx.connection(), ctx.responder());
        assertTrue(ctx.responder().hasResult());

        JsonArray tools = ctx.responder().lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        JsonObject tool = tools.getJsonObject(0);
        JsonObject outputSchema = tool.getJsonObject("outputSchema");
        assertNotNull(outputSchema);
        assertEquals("object", outputSchema.getString("type"));
        assertNotNull(outputSchema.getJsonObject("properties").getJsonObject("result"));
    }

    @Test
    public void testInputSchemaFallsBackWhenGeneratorInvalid() {
        ToolTestContext ctx = setupToolTest("bad-gen-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "bad-gen-tool",
                new MethodMetadata("badGenTool", "Tool with invalid generator", null, null,
                        List.of(new ArgumentMetadata("name", "A name", true, String.class)),
                        "org.test.TestTool", "java.lang.String"),
                null, false,
                "org.nonexistent.NoSuchGenerator", "", ""));

        ctx.handler().handle(jsonRpcRequest(3, "tools/list"), ctx.connection(), ctx.responder());
        assertTrue(ctx.responder().hasResult());

        JsonArray tools = ctx.responder().lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        JsonObject tool = tools.getJsonObject(0);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        assertNotNull("Should fall back to auto-generated schema", inputSchema);
        assertEquals("object", inputSchema.getString("type"));
        assertNotNull(inputSchema.getJsonObject("properties").getJsonObject("name"));
    }

    @Test
    public void testInputSchemaFallsBackWhenGeneratorDoesNotImplementInterface() {
        ToolTestContext ctx = setupToolTest("wrong-type-gen-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "wrong-type-gen-tool",
                new MethodMetadata("wrongTypeGenTool", "Tool with non-conforming generator", null, null,
                        List.of(new ArgumentMetadata("name", "A name", true, String.class)),
                        "org.test.TestTool", "java.lang.String"),
                null, false,
                "java.lang.String", "", ""));

        ctx.handler().handle(jsonRpcRequest(3, "tools/list"), ctx.connection(), ctx.responder());
        assertTrue(ctx.responder().hasResult());

        JsonArray tools = ctx.responder().lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        JsonObject tool = tools.getJsonObject(0);
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        assertNotNull("Should fall back to auto-generated schema", inputSchema);
        assertEquals("object", inputSchema.getString("type"));
        assertNotNull(inputSchema.getJsonObject("properties").getJsonObject("name"));
    }

    // ==================== Helper Methods ====================

    private void assertSingleHintEmitted(ToolAnnotations annotations, String annotationKey, boolean expectedValue) {
        JsonObject tool = listSingleTool(annotations, false);
        assertTrue(tool.containsKey("annotations"));
        JsonObject ann = tool.getJsonObject("annotations");
        assertEquals(expectedValue, ann.getBoolean(annotationKey));
    }

    private JsonObject listSingleTool(ToolAnnotations annotations, boolean structuredContent) {
        ToolTestContext ctx = setupToolTest("test-tool", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "test-tool",
                new MethodMetadata("testTool", "A test tool", null, null,
                        List.of(new ArgumentMetadata("input", "Test input", true, String.class)),
                        "org.test.TestTool", "java.lang.String"),
                annotations, structuredContent, "", "", ""));

        ctx.handler().handle(jsonRpcRequest(3, "tools/list"), ctx.connection(), ctx.responder());
        assertTrue(ctx.responder().hasResult());

        JsonArray tools = ctx.responder().lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        return tools.getJsonObject(0);
    }
}
