/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.Implementation;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.injection.elicitation.BooleanSchema;
import org.wildfly.extension.mcp.injection.elicitation.Elicitation;
import org.wildfly.extension.mcp.injection.elicitation.StringSchema;

public class ElicitationSenderImplTestCase {

    private static final Implementation CLIENT_INFO = new Implementation("test-client", "1.0");

    // ==================== isSupported() ====================

    @Test
    public void testIsSupportedWhenElicitationCapabilityPresent() {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);
        assertTrue(sender.isFormSupported());
    }

    @Test
    public void testIsNotSupportedWhenUrlOnlyCapability() {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of("url", java.util.Map.of()))));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);
        assertFalse("Form mode should not be supported when only url is declared", sender.isFormSupported());
        assertTrue("URL mode should be supported", sender.isUrlSupported());
    }

    @Test
    public void testIsNotSupportedWhenNoElicitationCapability() {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26", List.of());
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);
        assertFalse(sender.isFormSupported());
    }

    @Test
    public void testIsNotSupportedWhenInitializeRequestIsNull() {
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), null);
        assertFalse(sender.isFormSupported());
    }

    // ==================== send() when unsupported ====================

    @Test
    public void testSendThrowsWhenNotSupported() throws Exception {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26", List.of());
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);

        Elicitation elicitation = Elicitation.builder("Provide info")
                .addSchemaProperty("name", new StringSchema(true))
                .build();

        try {
            sender.send(elicitation);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("elicitation"));
        }
    }

    // ==================== Full round-trip ====================

    @Test
    public void testSendReceiveAcceptResponse() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));

        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation elicitation = Elicitation.builder("What is your username?")
                .addSchemaProperty("username", new StringSchema(true))
                .addSchemaProperty("notify", new BooleanSchema(false))
                .timeout(5_000)
                .build();

        // Call send() on a background thread since it blocks
        CompletableFuture<Elicitation.Response> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return sender.send(elicitation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for the outgoing elicitation/create message to arrive at responder
        long deadline = System.currentTimeMillis() + 2000;
        JsonObject outgoing = null;
        while (System.currentTimeMillis() < deadline) {
            outgoing = responder.lastMessage();
            if (outgoing != null) break;
            Thread.sleep(20);
        }
        assertNotNull("Sender should have sent an elicitation/create message", outgoing);
        assertEquals("elicitation/create", outgoing.getString("method"));

        // Verify the requestedSchema structure
        JsonObject params = outgoing.getJsonObject("params");
        assertEquals("What is your username?", params.getString("message"));
        JsonObject schema = params.getJsonObject("requestedSchema");
        assertEquals("object", schema.getString("type"));
        assertNotNull(schema.getJsonObject("properties").getJsonObject("username"));
        assertNotNull(schema.getJsonObject("properties").getJsonObject("notify"));
        assertTrue(schema.getJsonArray("required").contains(Json.createValue("username")));

        // Extract the request id and simulate the client accepting
        long requestId = outgoing.getJsonNumber("id").longValue();
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("result", Json.createObjectBuilder()
                        .add("action", "accept")
                        .add("content", Json.createObjectBuilder()
                                .add("username", "alice")
                                .add("notify", true)))
                .build();
        registry.handleResponse(clientResponse.get("id"), clientResponse);

        Elicitation.Response response = responseFuture.get(2, TimeUnit.SECONDS);
        assertNotNull(response);
        assertTrue(response.isAccepted());
        assertEquals("alice", response.getString("username"));
        assertTrue(response.getBoolean("notify"));
    }

    @Test
    public void testSendReceiveDeclineResponse() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation elicitation = Elicitation.builder("Confirm?")
                .addSchemaProperty("field", new StringSchema(true))
                .timeout(5_000)
                .build();

        CompletableFuture<Elicitation.Response> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return sender.send(elicitation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for outgoing message
        long deadline = System.currentTimeMillis() + 2000;
        JsonObject outgoing = null;
        while (System.currentTimeMillis() < deadline) {
            outgoing = responder.lastMessage();
            if (outgoing != null) break;
            Thread.sleep(20);
        }
        assertNotNull(outgoing);

        long requestId = outgoing.getJsonNumber("id").longValue();
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("result", Json.createObjectBuilder()
                        .add("action", "decline"))
                .build();
        registry.handleResponse(clientResponse.get("id"), clientResponse);

        Elicitation.Response response = responseFuture.get(2, TimeUnit.SECONDS);
        assertTrue(response.isDeclined());
        assertFalse(response.isAccepted());
    }

    // ==================== Timeout ====================

    @Test
    public void testTimeoutCleansUpRegistry() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation elicitation = Elicitation.builder("Quick timeout test")
                .addSchemaProperty("field", new StringSchema(false))
                .timeout(100) // 100 ms timeout
                .build();

        try {
            sender.send(elicitation);
            fail("Expected TimeoutException");
        } catch (TimeoutException e) {
            // expected
        }

        // After timeout, the registry entry should have been removed
        // Verify by checking that a subsequent handleResponse for that ID is a no-op
        JsonObject outgoing = responder.lastMessage();
        assertNotNull(outgoing);
        long requestId = outgoing.getJsonNumber("id").longValue();

        // remove() should return false (already cleaned up by timeout)
        assertFalse(registry.remove(requestId));
    }

    // ==================== isUrlSupported() ====================

    @Test
    public void testIsUrlSupportedWhenUrlPropertyPresent() {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of("form", java.util.Map.of(), "url", java.util.Map.of()))));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);
        assertTrue(sender.isUrlSupported());
    }

    @Test
    public void testIsUrlNotSupportedWhenNoUrlProperty() {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);
        assertTrue(sender.isFormSupported());
        assertFalse(sender.isUrlSupported());
    }

    @Test
    public void testIsUrlNotSupportedWhenNoElicitationCapability() {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26", List.of());
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);
        assertFalse(sender.isUrlSupported());
    }

    // ==================== send() URL mode when unsupported ====================

    @Test
    public void testSendUrlThrowsWhenNotSupported() throws Exception {
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(
                new PendingRequestRegistry(), new TestResponder(), req);

        Elicitation urlReq = Elicitation.urlBuilder("Please authenticate",
                        "https://example.com/oauth")
                .elicitationId("auth-123")
                .build();

        try {
            sender.send(urlReq);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("URL"));
        }
    }

    // ==================== send() URL mode full round-trip ====================

    @Test
    public void testSendUrlAcceptResponse() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation",
                        java.util.Map.of("form", java.util.Map.of(), "url", java.util.Map.of()))));

        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation urlReq = Elicitation.urlBuilder("Please authenticate",
                    "https://example.com/oauth")
                .elicitationId("auth-456")
                .timeout(5_000)
                .build();

        CompletableFuture<Elicitation.Response> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return sender.send(urlReq);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long deadline = System.currentTimeMillis() + 2000;
        JsonObject outgoing = null;
        while (System.currentTimeMillis() < deadline) {
            outgoing = responder.lastMessage();
            if (outgoing != null) break;
            Thread.sleep(20);
        }
        assertNotNull("Sender should have sent an elicitation/create message", outgoing);
        assertEquals("elicitation/create", outgoing.getString("method"));

        JsonObject params = outgoing.getJsonObject("params");
        assertEquals("url", params.getString("mode"));
        assertEquals("https://example.com/oauth", params.getString("url"));
        assertEquals("auth-456", params.getString("elicitationId"));
        assertEquals("Please authenticate", params.getString("message"));

        long requestId = outgoing.getJsonNumber("id").longValue();
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("result", Json.createObjectBuilder()
                        .add("action", "accept"))
                .build();
        registry.handleResponse(clientResponse.get("id"), clientResponse);

        Elicitation.Response response = responseFuture.get(2, TimeUnit.SECONDS);
        assertNotNull(response);
        assertTrue(response.isAccepted());
        assertTrue(response.content().isEmpty());
    }

    @Test
    public void testSendUrlDeclineResponse() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation",
                        java.util.Map.of("form", java.util.Map.of(), "url", java.util.Map.of()))));

        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation urlReq = Elicitation.urlBuilder("Please authenticate",
                    "https://example.com/oauth")
                .elicitationId("auth-789")
                .timeout(5_000)
                .build();

        CompletableFuture<Elicitation.Response> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return sender.send(urlReq);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long deadline = System.currentTimeMillis() + 2000;
        JsonObject outgoing = null;
        while (System.currentTimeMillis() < deadline) {
            outgoing = responder.lastMessage();
            if (outgoing != null) break;
            Thread.sleep(20);
        }
        assertNotNull(outgoing);

        long requestId = outgoing.getJsonNumber("id").longValue();
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("result", Json.createObjectBuilder()
                        .add("action", "decline"))
                .build();
        registry.handleResponse(clientResponse.get("id"), clientResponse);

        Elicitation.Response response = responseFuture.get(2, TimeUnit.SECONDS);
        assertTrue(response.isDeclined());
        assertFalse(response.isAccepted());
        assertFalse(response.isCancelled());
        assertTrue(response.content().isEmpty());
    }

    // ==================== send() URL mode timeout ====================

    @Test
    public void testSendUrlTimeoutCleansUpRegistry() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation",
                        java.util.Map.of("form", java.util.Map.of(), "url", java.util.Map.of()))));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation urlReq = Elicitation.urlBuilder("Quick timeout",
                    "https://example.com/timeout")
                .elicitationId("timeout-001")
                .timeout(100)
                .build();

        try {
            sender.send(urlReq);
            fail("Expected TimeoutException");
        } catch (TimeoutException e) {
            // expected
        }

        JsonObject outgoing = responder.lastMessage();
        assertNotNull(outgoing);
        long requestId = outgoing.getJsonNumber("id").longValue();
        assertFalse(registry.remove(requestId));
    }

    // ==================== notifyElicitationComplete() ====================

    @Test
    public void testNotifyElicitationCompleteSendsNotification() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation",
                        java.util.Map.of("form", java.util.Map.of(), "url", java.util.Map.of()))));
        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        sender.notifyElicitationComplete("auth-456");

        JsonObject notification = responder.lastMessage();
        assertNotNull("A notification should have been sent", notification);
        assertEquals("notifications/elicitation/complete", notification.getString("method"));
        assertFalse("Notification should not have an id", notification.containsKey("id"));
        assertEquals("auth-456", notification.getJsonObject("params").getString("elicitationId"));
    }

    // ==================== send() includes mode: form ====================

    @Test
    public void testSendIncludesModeForm() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        TestResponder responder = new TestResponder();
        InitializeRequest req = new InitializeRequest(CLIENT_INFO, "2025-03-26",
                List.of(new ClientCapability("elicitation", java.util.Map.of())));

        ElicitationSenderImpl sender = new ElicitationSenderImpl(registry, responder, req);

        Elicitation elicitation = Elicitation.builder("Name?")
                .addSchemaProperty("name", new StringSchema(true))
                .timeout(5_000)
                .build();

        CompletableFuture<Elicitation.Response> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return sender.send(elicitation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        long deadline = System.currentTimeMillis() + 2000;
        JsonObject outgoing = null;
        while (System.currentTimeMillis() < deadline) {
            outgoing = responder.lastMessage();
            if (outgoing != null) break;
            Thread.sleep(20);
        }
        assertNotNull(outgoing);
        assertEquals("form", outgoing.getJsonObject("params").getString("mode"));

        // Complete the future to clean up the background thread
        long requestId = outgoing.getJsonNumber("id").longValue();
        JsonObject clientResponse = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", requestId)
                .add("result", Json.createObjectBuilder()
                        .add("action", "accept")
                        .add("content", Json.createObjectBuilder()
                                .add("name", "test")))
                .build();
        registry.handleResponse(clientResponse.get("id"), clientResponse);
        responseFuture.get(2, TimeUnit.SECONDS);
    }
}
