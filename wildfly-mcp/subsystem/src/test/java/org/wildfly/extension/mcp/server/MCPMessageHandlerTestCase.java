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
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class MCPMessageHandlerTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private TestMCPConnection connection;
    private ConnectionManager connectionManager;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();

        // Register a test tool
        registry.addTool("echo", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "echo",
                new MethodMetadata("echo", "Echoes the input", null, null,
                        List.of(new ArgumentMetadata("message", "The message to echo", true, String.class)),
                        "org.test.EchoTool", "java.lang.String")));

        // Register a test tool with optional param
        registry.addTool("greet", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "greet",
                new MethodMetadata("greet", "Greets someone", null, null,
                        List.of(
                                new ArgumentMetadata("name", "Person's name", true, String.class),
                                new ArgumentMetadata("title", "Optional title", false, String.class)),
                        "org.test.GreetTool", "java.lang.String")));

        // Register a test prompt
        registry.addPrompt("code-review", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.PROMPT, "code-review",
                new MethodMetadata("codeReview", "Code review prompt", null, null,
                        List.of(new ArgumentMetadata("code", "The code to review", true, String.class)),
                        "org.test.CodeReviewPrompt", "java.lang.String")));

        // Register a test resource
        registry.addResource("file:///logs/server.log", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE, "server-log",
                new MethodMetadata("serverLog", "Server log file", "file:///logs/server.log", "text/plain",
                        List.of(),
                        "org.test.ServerLogResource", "java.lang.String")));

        // Register a test resource template
        registry.addResourceTemplate("db:///{database}/tables/{table}", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.RESOURCE_TEMPLATE, "db-table",
                new MethodMetadata("readTable", "Read a database table", "db:///{database}/tables/{table}", "application/json",
                        List.of(
                                new ArgumentMetadata("database", "Database name", true, String.class),
                                new ArgumentMetadata("table", "Table name", true, String.class)),
                        "org.test.DbResource", "java.lang.String")));

        connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");

        responder = new TestResponder();
        connection = new TestMCPConnection("test-connection-1");
        connectionManager.add(connection);
    }

    // ==================== Lifecycle Tests ====================

    @Test
    public void testInitialize() {
        assertEquals(MCPConnection.Status.NEW, connection.status());

        handler.handle(initializeMessage(1), connection, responder);

        assertEquals(MCPConnection.Status.INITIALIZING, connection.status());
        assertTrue(responder.hasResult());
        JsonObject result = responder.lastResult();
        assertEquals("2025-03-26", result.getString("protocolVersion"));
        assertNotNull(result.getJsonObject("serverInfo"));
        assertEquals("test-server", result.getJsonObject("serverInfo").getString("name"));
        assertNotNull(result.getJsonObject("capabilities"));
    }

    @Test
    public void testInitializeAdvertisesCapabilities() {
        handler.handle(initializeMessage(1), connection, responder);

        JsonObject capabilities = responder.lastResult().getJsonObject("capabilities");
        assertNotNull(capabilities.getJsonObject("tools"));
        assertNotNull(capabilities.getJsonObject("prompts"));
        assertNotNull(capabilities.getJsonObject("resources"));
        assertNotNull(capabilities.getJsonObject("completions"));
    }

    @Test
    public void testInitializeRequiresInitializeMethod() {
        handler.handle(jsonRpcRequest(1, "ping"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32601, responder.lastError().getInt("code"));
    }

    @Test
    public void testInitializedNotification() {
        handler.handle(initializeMessage(1), connection, responder);
        assertEquals(MCPConnection.Status.INITIALIZING, connection.status());

        handler.handle(jsonRpcNotification("notifications/initialized"), connection, responder);

        assertEquals(MCPConnection.Status.IN_OPERATION, connection.status());
    }

    @Test
    public void testPingDuringInitializing() {
        handler.handle(initializeMessage(1), connection, responder);
        responder.clear();

        handler.handle(jsonRpcRequest(2, "ping"), connection, responder);

        assertTrue(responder.hasResult());
    }

    @Test
    public void testRejectOperationsDuringInitializing() {
        handler.handle(initializeMessage(1), connection, responder);
        responder.clear();

        handler.handle(jsonRpcRequest(2, "tools/list"), connection, responder);

        assertTrue(responder.hasError());
    }

    @Test
    public void testResponsesAreDiscarded() {
        moveToOperation();
        responder.clear();

        // A message with both "result" and/or "error" is a response
        JsonObject responseMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 99)
                .add("result", Json.createObjectBuilder())
                .build();
        handler.handle(responseMessage, connection, responder);

        // No response should be sent back
        assertTrue(responder.allMessages().isEmpty());
    }

    // ==================== Ping Test ====================

    @Test
    public void testPing() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(2, "ping"), connection, responder);

        assertTrue(responder.hasResult());
        assertNotNull(responder.lastResult());
    }

    // ==================== Tools Tests ====================

    @Test
    public void testToolsList() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(3, "tools/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray tools = responder.lastResult().getJsonArray("tools");
        assertNotNull(tools);
        assertEquals(2, tools.size());

        // Verify tool structure
        boolean foundEcho = false;
        boolean foundGreet = false;
        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.getJsonObject(i);
            String name = tool.getString("name");
            if ("echo".equals(name)) {
                foundEcho = true;
                assertEquals("Echoes the input", tool.getString("description"));
                JsonObject inputSchema = tool.getJsonObject("inputSchema");
                assertNotNull(inputSchema);
                assertEquals("object", inputSchema.getString("type"));
                assertNotNull(inputSchema.getJsonObject("properties").getJsonObject("message"));
                assertEquals(1, inputSchema.getJsonArray("required").size());
            } else if ("greet".equals(name)) {
                foundGreet = true;
                assertEquals("Greets someone", tool.getString("description"));
                JsonObject inputSchema = tool.getJsonObject("inputSchema");
                // Only "name" is required, "title" is optional
                assertEquals(1, inputSchema.getJsonArray("required").size());
                assertEquals("name", inputSchema.getJsonArray("required").getString(0));
            }
        }
        assertTrue("Echo tool should be listed", foundEcho);
        assertTrue("Greet tool should be listed", foundGreet);
    }

    // ==================== Prompts Tests ====================

    @Test
    public void testPromptsList() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(4, "prompts/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray prompts = responder.lastResult().getJsonArray("prompts");
        assertNotNull(prompts);
        assertEquals(1, prompts.size());

        JsonObject prompt = prompts.getJsonObject(0);
        assertEquals("code-review", prompt.getString("name"));
        assertEquals("Code review prompt", prompt.getString("description"));
        JsonArray args = prompt.getJsonArray("arguments");
        assertEquals(1, args.size());
        assertEquals("code", args.getJsonObject(0).getString("name"));
        assertTrue(args.getJsonObject(0).getBoolean("required"));
    }

    // ==================== Null Params Validation Tests ====================

    @Test
    public void testToolsCallMissingParams() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(20, "tools/call"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testPromptsGetMissingParams() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(21, "prompts/get"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testResourcesReadMissingParams() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(22, "resources/read"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    // ==================== Resources Tests ====================

    @Test
    public void testResourcesSubscribeCapabilityAdvertised() {
        handler.handle(initializeMessage(1), connection, responder);

        JsonObject resources = responder.lastResult().getJsonObject("capabilities").getJsonObject("resources");
        assertNotNull(resources);
        assertTrue(resources.getBoolean("subscribe"));
    }

    @Test
    public void testResourcesSubscribe() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 23)
                .add("method", "resources/subscribe")
                .add("params", Json.createObjectBuilder()
                        .add("uri", "file:///logs/server.log"))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasResult());
    }

    @Test
    public void testResourcesSubscribeMissingParams() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(24, "resources/subscribe"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testResourcesSubscribeMissingUri() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 25)
                .add("method", "resources/subscribe")
                .add("params", Json.createObjectBuilder())
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testResourcesUnsubscribe() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 26)
                .add("method", "resources/unsubscribe")
                .add("params", Json.createObjectBuilder()
                        .add("uri", "file:///logs/server.log"))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasResult());
    }

    @Test
    public void testResourcesUnsubscribeMissingParams() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(27, "resources/unsubscribe"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testResourcesUnsubscribeMissingUri() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 28)
                .add("method", "resources/unsubscribe")
                .add("params", Json.createObjectBuilder())
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32602, responder.lastError().getInt("code"));
    }

    @Test
    public void testResourcesList() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(5, "resources/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray resources = responder.lastResult().getJsonArray("resources");
        assertNotNull(resources);
        assertEquals(1, resources.size());

        JsonObject resource = resources.getJsonObject(0);
        assertEquals("server-log", resource.getString("name"));
        assertEquals("file:///logs/server.log", resource.getString("uri"));
        assertEquals("text/plain", resource.getString("mimeType"));
    }

    // ==================== Resource Templates Tests ====================

    @Test
    public void testResourceTemplatesList() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(6, "resources/templates/list"), connection, responder);

        assertTrue(responder.hasResult());
        JsonArray templates = responder.lastResult().getJsonArray("resourceTemplates");
        assertNotNull(templates);
        assertEquals(1, templates.size());

        JsonObject template = templates.getJsonObject(0);
        assertEquals("db-table", template.getString("name"));
        assertEquals("db:///{database}/tables/{table}", template.getString("uriTemplate"));
        assertEquals("application/json", template.getString("mimeType"));
    }

    // ==================== Completion Tests ====================

    @Test
    public void testCompletionCompletePromptNoHandler() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 7)
                .add("method", "completion/complete")
                .add("params", Json.createObjectBuilder()
                        .add("ref", Json.createObjectBuilder()
                                .add("type", "ref/prompt")
                                .add("name", "code-review"))
                        .add("argument", Json.createObjectBuilder()
                                .add("name", "code")
                                .add("value", "pub")))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasResult());
        JsonObject completion = responder.lastResult().getJsonObject("completion");
        assertNotNull(completion);
        assertNotNull(completion.getJsonArray("values"));
        assertEquals(0, completion.getJsonArray("values").size());
        assertFalse(completion.getBoolean("hasMore"));
    }

    @Test
    public void testCompletionCompleteResourceNoHandler() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 8)
                .add("method", "completion/complete")
                .add("params", Json.createObjectBuilder()
                        .add("ref", Json.createObjectBuilder()
                                .add("type", "ref/resource")
                                .add("name", "db-table"))
                        .add("argument", Json.createObjectBuilder()
                                .add("name", "database")
                                .add("value", "my")))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasResult());
        JsonObject completion = responder.lastResult().getJsonObject("completion");
        assertNotNull(completion);
        assertEquals(0, completion.getJsonArray("values").size());
    }

    @Test
    public void testCompletionMissingRef() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 9)
                .add("method", "completion/complete")
                .add("params", Json.createObjectBuilder()
                        .add("argument", Json.createObjectBuilder()
                                .add("name", "code")
                                .add("value", "x")))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasError());
    }

    @Test
    public void testCompletionUnsupportedRefType() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 10)
                .add("method", "completion/complete")
                .add("params", Json.createObjectBuilder()
                        .add("ref", Json.createObjectBuilder()
                                .add("type", "ref/unknown")
                                .add("name", "foo"))
                        .add("argument", Json.createObjectBuilder()
                                .add("name", "bar")
                                .add("value", "baz")))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasError());
    }

    @Test
    public void testCompletionCompletePromptWithContext() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 11)
                .add("method", "completion/complete")
                .add("params", Json.createObjectBuilder()
                        .add("ref", Json.createObjectBuilder()
                                .add("type", "ref/prompt")
                                .add("name", "code-review"))
                        .add("argument", Json.createObjectBuilder()
                                .add("name", "language")
                                .add("value", "py"))
                        .add("context", Json.createObjectBuilder()
                                .add("arguments", Json.createObjectBuilder()
                                        .add("code", "def hello(): pass"))))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasResult());
        JsonObject completion = responder.lastResult().getJsonObject("completion");
        assertNotNull(completion);
        assertEquals(0, completion.getJsonArray("values").size());
        assertFalse(completion.getBoolean("hasMore"));
    }

    @Test
    public void testCompletionCompleteResourceWithContext() {
        moveToOperation();
        responder.clear();

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 12)
                .add("method", "completion/complete")
                .add("params", Json.createObjectBuilder()
                        .add("ref", Json.createObjectBuilder()
                                .add("type", "ref/resource")
                                .add("name", "db-table"))
                        .add("argument", Json.createObjectBuilder()
                                .add("name", "table")
                                .add("value", "us"))
                        .add("context", Json.createObjectBuilder()
                                .add("arguments", Json.createObjectBuilder()
                                        .add("database", "mydb"))))
                .build();
        handler.handle(message, connection, responder);

        assertTrue(responder.hasResult());
        JsonObject completion = responder.lastResult().getJsonObject("completion");
        assertNotNull(completion);
        assertEquals(0, completion.getJsonArray("values").size());
    }


    // ==================== Cancellation Test ====================

    @Test
    public void testNotificationsCancelled() {
        moveToOperation();
        assertFalse(connection.isCancelled());

        handler.handle(jsonRpcNotification("notifications/cancelled"), connection, responder);

        assertTrue(connection.isCancelled());
    }

    // ==================== Error Handling Tests ====================

    @Test
    public void testUnsupportedMethod() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(14, "unsupported/method"), connection, responder);

        assertTrue(responder.hasError());
        assertEquals(-32601, responder.lastError().getInt("code"));
    }

    @Test
    public void testShutdownConnectionRejects() {
        moveToOperation();
        connection.close();
        assertEquals(MCPConnection.Status.SHUTDOWN, connection.status());
        responder.clear();

        handler.handle(jsonRpcRequest(15, "ping"), connection, responder);

        assertTrue(responder.hasError());
    }

    // ==================== Close Test ====================

    @Test
    public void testCloseConnection() {
        moveToOperation();
        responder.clear();

        handler.handle(jsonRpcRequest(16, "q/close"), connection, responder);

        // Connection should be removed from the manager
    }

    // ==================== Helper Methods ====================

    private void moveToOperation() {
        handler.handle(initializeMessage(1), connection, responder);
        handler.handle(jsonRpcNotification("notifications/initialized"), connection, responder);
        assertEquals(MCPConnection.Status.IN_OPERATION, connection.status());
    }

    private JsonObject initializeMessage(int id) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", "initialize")
                .add("params", Json.createObjectBuilder()
                        .add("protocolVersion", "2025-03-26")
                        .add("clientInfo", Json.createObjectBuilder()
                                .add("name", "test-client")
                                .add("version", "1.0.0"))
                        .add("capabilities", Json.createObjectBuilder()))
                .build();
    }

    private JsonObject jsonRpcRequest(int id, String method) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", method)
                .build();
    }

    private JsonObject jsonRpcNotification(String method) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", method)
                .build();
    }
}
