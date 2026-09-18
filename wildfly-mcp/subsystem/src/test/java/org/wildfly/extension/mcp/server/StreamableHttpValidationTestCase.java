/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.MCPMethods;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;

/**
 * Tests for Streamable HTTP transport validation logic that can be exercised
 * at the unit level (JSON parsing, batch rejection, notification handling).
 */
public class StreamableHttpValidationTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private ConnectionManager connectionManager;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(), "test-server", "1.0.0");
        responder = new TestResponder();
    }

    // ---- Task 5: Notification detection (no id field) ----

    @Test
    public void testNotificationHasNoId() {
        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/cancelled")
                .build();
        assertTrue("Notification should not have id", !notification.containsKey("id"));
    }

    @Test
    public void testRequestHasId() {
        JsonObject request = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "tools/list")
                .build();
        assertTrue("Request should have id", request.containsKey("id"));
    }

    @Test
    public void testNotificationProcessedByHandler() {
        TestMCPConnection connection = new TestMCPConnection("test-conn");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);
        responder.clear();

        JsonObject notification = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/cancelled")
                .build();

        handler.handle(notification, connection, responder);
        // Notification should be processed without sending a response (cancel is silent)
        assertTrue("Cancel notification should be accepted", connection.isCancelled());
    }

    // ---- Task 5: Unknown session handling ----

    @Test
    public void testUnknownSessionIdReturnsNull() {
        assertNotNull("Connection manager should be initialized", connectionManager);
        assertEquals("Unknown session should return null", null, connectionManager.get("nonexistent-session-id"));
    }

    // ---- Task 8: Mcp-Param-* header matching ----

    @Test
    public void testMcpParamValueNormalization() {
        JsonObject params = Json.createObjectBuilder()
                .add("arguments", Json.createObjectBuilder()
                        .add("message", "hello")
                        .add("count", 42)
                        .add("flag", true))
                .build();

        JsonObject arguments = params.getJsonObject("arguments");

        // String value: JsonString.getString() strips quotes
        assertEquals("hello", ((jakarta.json.JsonString) arguments.get("message")).getString());
        // Number value: toString gives the number
        assertEquals("42", arguments.get("count").toString());
        // Boolean value: toString gives the boolean
        assertEquals("true", arguments.get("flag").toString());
    }

    // ---- Task 2: error code constants ----

    @Test
    public void testHeaderMismatchErrorCodeDefined() {
        assertEquals(-32020, MCPMethods.HEADER_MISMATCH);
    }

    @Test
    public void testUnsupportedProtocolVersionErrorCodeDefined() {
        assertEquals(-32022, MCPMethods.UNSUPPORTED_PROTOCOL_VERSION);
    }

    @Test
    public void testMissingRequiredClientCapabilityErrorCodeDefined() {
        assertEquals(-32021, MCPMethods.MISSING_REQUIRED_CLIENT_CAPABILITY);
    }
}
