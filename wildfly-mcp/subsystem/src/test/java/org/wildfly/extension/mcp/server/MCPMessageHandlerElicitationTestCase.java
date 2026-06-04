/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.initializeMessage;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;

public class MCPMessageHandlerElicitationTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private TestMCPConnection connection;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        ConnectionManager connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");
        responder = new TestResponder();
        connection = new TestMCPConnection("elicitation-test");
        connectionManager.add(connection);
    }

    // ==================== Capability advertisement ====================

    @Test
    public void testElicitationAdvertisedInCapabilities() {
        handler.handle(initializeMessage(), connection, responder);

        JsonObject capabilities = responder.lastResult().getJsonObject("capabilities");
        assertNotNull("elicitation capability must be advertised", capabilities.getJsonObject("elicitation"));
    }

    // ==================== Response routing ====================

    @Test
    public void testClientResponseRoutedToPendingRegistry() throws Exception {
        moveToOperation();

        // Pre-register a future in the connection's registry so the response has somewhere to go
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        long requestId = connection.pendingRequests().register(future);

        // Simulate a client response for that id
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("result", Json.createObjectBuilder()
                        .add("action", "accept")
                        .add("content", Json.createObjectBuilder()
                                .add("value", "hello")))
                .build();

        handler.handle(clientResponse, connection, responder);

        // The response should be routed to the future — no message sent back to client
        assertTrue("No message should be sent back to client for a response", responder.allMessages().isEmpty());

        // The future should be completed
        JsonObject received = future.get(1, TimeUnit.SECONDS);
        assertNotNull(received);
        assertTrue(received.containsKey("result"));
    }

    @Test
    public void testClientResponseWithUnknownIdSilentlyDropped() {
        moveToOperation();

        // A response with an id that has no registered future
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 99999)
                .add("result", Json.createObjectBuilder())
                .build();

        handler.handle(clientResponse, connection, responder);

        // No error, no reply
        assertTrue(responder.allMessages().isEmpty());
    }

    @Test
    public void testClientErrorResponseRoutedToPendingRegistry() throws Exception {
        moveToOperation();

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        long requestId = connection.pendingRequests().register(future);

        // Client can also send an error response (e.g. on cancel)
        JsonObject errorResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("error", Json.createObjectBuilder()
                        .add("code", -32000)
                        .add("message", "User cancelled"))
                .build();

        handler.handle(errorResponse, connection, responder);

        assertTrue(responder.allMessages().isEmpty());
        JsonObject received = future.get(1, TimeUnit.SECONDS);
        assertNotNull(received);
        assertTrue(received.containsKey("error"));
    }

    // ==================== Elicitation capability with client advertising it ====================

    @Test
    public void testInitializeWithElicitationClientCapability() {
        JsonObject initMsg = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "initialize")
                .add("params", Json.createObjectBuilder()
                        .add("protocolVersion", "2025-03-26")
                        .add("clientInfo", Json.createObjectBuilder()
                                .add("name", "mcp-client")
                                .add("version", "2.0"))
                        .add("capabilities", Json.createObjectBuilder()
                                .add("elicitation", Json.createObjectBuilder())))
                .build();

        handler.handle(initMsg, connection, responder);

        assertTrue(responder.hasResult());
        // Connection should now know client supports elicitation
        assertTrue(connection.initializeRequest().supportsElicitation());
    }

    // ==================== Helpers ====================

    private void moveToOperation() {
        MCPTestHelpers.moveToOperation(handler, connection, responder);
    }
}
