/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.initializeMessage;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcNotification;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.Base64;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.Cursor;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

/**
 * Tests for cursor-based pagination on tools/list, prompts/list,
 * resources/list and resources/templates/list.
 */
public class PaginationTestCase {

    // page size = 1: every list has 2 items, so first page returns 1 + nextCursor,
    // second page returns 1 with no nextCursor.
    private static final int PAGE_SIZE = 1;

    private WildFlyMCPRegistry registry;
    private MCPMessageHandler handler;
    private TestResponder responder;
    private TestMCPConnection connection;

    @Before
    public void setUp() {
        registry = new WildFlyMCPRegistry();

        registry.addTool("alpha", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "alpha",
                new MethodMetadata("alpha", "Alpha tool", null, null, List.of(), "org.test.T", "java.lang.String")));
        registry.addTool("beta", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "beta",
                new MethodMetadata("beta", "Beta tool", null, null, List.of(), "org.test.T", "java.lang.String")));

        registry.addPrompt("a-prompt", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.PROMPT, "a-prompt",
                new MethodMetadata("aPrompt", "A prompt", null, null, List.of(), "org.test.P", "java.lang.String")));
        registry.addPrompt("b-prompt", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.PROMPT, "b-prompt",
                new MethodMetadata("bPrompt", "B prompt", null, null, List.of(), "org.test.P", "java.lang.String")));

        registry.addResource("file:///a.log", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "a-resource",
                new MethodMetadata("aRes", "A resource", "file:///a.log", "text/plain", List.of(), "org.test.R", "java.lang.String")));
        registry.addResource("file:///b.log", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "b-resource",
                new MethodMetadata("bRes", "B resource", "file:///b.log", "text/plain", List.of(), "org.test.R", "java.lang.String")));

        registry.addResourceTemplate("tmpl:///{a}", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE, "a-template",
                new MethodMetadata("aTmpl", "A template", "tmpl:///{a}", "application/json", List.of(), "org.test.RT", "java.lang.String")));
        registry.addResourceTemplate("tmpl:///{b}", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE, "b-template",
                new MethodMetadata("bTmpl", "B template", "tmpl:///{b}", "application/json", List.of(), "org.test.RT", "java.lang.String")));

        ConnectionManager connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(),
                "test-server", "1.0.0", PAGE_SIZE);

        responder = new TestResponder();
        connection = new TestMCPConnection("test-connection-1");
        connectionManager.add(connection);
        moveToOperation();
    }

    // ==================== tools/list ====================

    @Test
    public void testToolsListFirstPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(10, "tools/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray tools = responder.lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        assertEquals("alpha", tools.getJsonObject(0).getString("name"));
        assertNotNull(responder.lastResult().getString("nextCursor"));
    }

    @Test
    public void testToolsListSecondPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(10, "tools/list"), connection, responder);
        String cursor = responder.lastResult().getString("nextCursor");
        responder.clear();

        handler.handle(listRequestWithCursor(11, "tools/list", cursor), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray tools = responder.lastResult().getJsonArray("tools");
        assertEquals(1, tools.size());
        assertEquals("beta", tools.getJsonObject(0).getString("name"));
        assertNull(responder.lastResult().getString("nextCursor", null));
    }

    @Test
    public void testToolsListNoPaginationWhenDisabled() {
        ConnectionManager cm = new ConnectionManager();
        MCPMessageHandler noPagingHandler = new MCPMessageHandler(cm, registry,
                getClass().getClassLoader(), "test-server", "1.0.0", 0);
        TestMCPConnection conn = new TestMCPConnection("no-page-conn");
        cm.add(conn);
        TestResponder resp = new TestResponder();
        noPagingHandler.handle(initializeMessage(1), conn, resp);
        noPagingHandler.handle(jsonRpcNotification("notifications/initialized"), conn, resp);
        resp.clear();

        noPagingHandler.handle(jsonRpcRequest(2, "tools/list"), conn, resp);

        JsonArray tools = resp.lastResult().getJsonArray("tools");
        assertEquals(2, tools.size());
        assertNull(resp.lastResult().getString("nextCursor", null));
    }

    // ==================== prompts/list ====================

    @Test
    public void testPromptsListFirstPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(20, "prompts/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray prompts = responder.lastResult().getJsonArray("prompts");
        assertEquals(1, prompts.size());
        assertEquals("a-prompt", prompts.getJsonObject(0).getString("name"));
        assertNotNull(responder.lastResult().getString("nextCursor"));
    }

    @Test
    public void testPromptsListSecondPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(20, "prompts/list"), connection, responder);
        String cursor = responder.lastResult().getString("nextCursor");
        responder.clear();

        handler.handle(listRequestWithCursor(21, "prompts/list", cursor), connection, responder);

        JsonArray prompts = responder.lastResult().getJsonArray("prompts");
        assertEquals(1, prompts.size());
        assertEquals("b-prompt", prompts.getJsonObject(0).getString("name"));
        assertNull(responder.lastResult().getString("nextCursor", null));
    }

    // ==================== resources/list ====================

    @Test
    public void testResourcesListFirstPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(30, "resources/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray resources = responder.lastResult().getJsonArray("resources");
        assertEquals(1, resources.size());
        assertEquals("a-resource", resources.getJsonObject(0).getString("name"));
        assertNotNull(responder.lastResult().getString("nextCursor"));
    }

    @Test
    public void testResourcesListSecondPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(30, "resources/list"), connection, responder);
        String cursor = responder.lastResult().getString("nextCursor");
        responder.clear();

        handler.handle(listRequestWithCursor(31, "resources/list", cursor), connection, responder);

        JsonArray resources = responder.lastResult().getJsonArray("resources");
        assertEquals(1, resources.size());
        assertEquals("b-resource", resources.getJsonObject(0).getString("name"));
        assertNull(responder.lastResult().getString("nextCursor", null));
    }

    // ==================== resources/templates/list ====================

    @Test
    public void testResourceTemplatesListFirstPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(40, "resources/templates/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray templates = responder.lastResult().getJsonArray("resourceTemplates");
        assertEquals(1, templates.size());
        assertEquals("a-template", templates.getJsonObject(0).getString("name"));
        assertNotNull(responder.lastResult().getString("nextCursor"));
    }

    @Test
    public void testResourceTemplatesListSecondPage() {
        responder.clear();
        handler.handle(jsonRpcRequest(40, "resources/templates/list"), connection, responder);
        String cursor = responder.lastResult().getString("nextCursor");
        responder.clear();

        handler.handle(listRequestWithCursor(41, "resources/templates/list", cursor), connection, responder);

        JsonArray templates = responder.lastResult().getJsonArray("resourceTemplates");
        assertEquals(1, templates.size());
        assertEquals("b-template", templates.getJsonObject(0).getString("name"));
        assertNull(responder.lastResult().getString("nextCursor", null));
    }

    // ==================== Cursor stale / malformed cursor ====================

    @Test
    public void testStaleCursorResetsToFirstPage() {
        // Encode a cursor for an item that has been removed from the list
        String staleCursor = Cursor.encode("deleted-item");
        List<String> items = List.of("alpha", "beta", "gamma");

        Cursor.Page<String> page = Cursor.paginate(items, staleCursor, 2, s -> s);

        assertEquals(2, page.items().size());
        assertEquals("alpha", page.items().get(0));
        assertEquals("beta", page.items().get(1));
    }

    @Test
    public void testStaleCursorWithPageSizeZeroReturnsAll() {
        String staleCursor = Cursor.encode("ghost");
        List<String> items = List.of("alpha", "beta");

        Cursor.Page<String> page = Cursor.paginate(items, staleCursor, 0, s -> s);

        assertEquals(2, page.items().size());
    }

    @Test
    public void testMalformedCursorResetsToFirstPage() {
        List<String> items = List.of("alpha", "beta", "gamma");

        Cursor.Page<String> page = Cursor.paginate(items, "!!!not-valid-base64!!!", 2, s -> s);

        assertEquals(2, page.items().size());
        assertEquals("alpha", page.items().get(0));
    }

    // ==================== Cursor encoding ====================

    @Test
    public void testCursorEncodeDecodeRoundtrip() {
        String name = "my-tool_v2.0";
        assertEquals(name, Cursor.decode(Cursor.encode(name)));
    }

    @Test
    public void testCursorIsBase64() {
        String cursor = Cursor.encode("some-tool");
        // Should be decodable by standard base64 decoder
        assertNotNull(Base64.getDecoder().decode(cursor));
        assertFalse(cursor.isEmpty());
    }

    // ==================== Helpers ====================

    private void moveToOperation() {
        MCPTestHelpers.moveToOperation(handler, connection, responder);
    }

    private JsonObject listRequestWithCursor(int id, String method, String cursor) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", method)
                .add("params", Json.createObjectBuilder().add("cursor", cursor))
                .build();
    }
}
