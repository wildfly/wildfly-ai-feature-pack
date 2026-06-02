/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for MCP elicitation (form mode and URL mode).
 */
public class ElicitationIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    // ==================== Form Mode Elicitation ====================

    @Test
    public void testElicitationToolListedWithoutSenderParam() throws Exception {
        String response = sendAndReceive("tools/list", null);
        assertThat(response).as("Should list the greet-with-name tool").contains("greet-with-name");
        assertThat(response).as("ElicitationSender must not appear in inputSchema").doesNotContain("ElicitationSender");

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        JsonArray tools = json.getJsonObject("result").getJsonArray("tools");
        JsonObject greetTool = null;
        for (int i = 0; i < tools.size(); i++) {
            if ("greet-with-name".equals(tools.getJsonObject(i).getString("name"))) {
                greetTool = tools.getJsonObject(i);
                break;
            }
        }
        assertThat(greetTool).as("greet-with-name tool should be present").isNotNull();
        JsonArray required = greetTool.getJsonObject("inputSchema").getJsonArray("required");
        assertThat(required).as("greet-with-name should have empty required array").isEmpty();
    }

    @Test
    public void testElicitationAcceptRoundTrip() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"greet-with-name","arguments":{}}}"""
                .formatted(toolCallId);

        postToStreamable(toolCallMessage);

        String elicitationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should be an elicitation/create request").contains("elicitation/create");
        assertThat(elicitationJson).as("Should contain the prompt message").contains("What is your name?");
        assertThat(elicitationJson).as("Should contain requestedSchema").contains("requestedSchema");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"accept","content":{"name":"WildFly"}}}"""
                .formatted(elicitationId);
        int statusCode = postToStreamable(clientResponse);
        assertThat(statusCode).as("Client response POST should succeed").isEqualTo(200);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after elicitation").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();

        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content).as("Should contain content array").isNotNull();
        assertThat(content.getJsonObject(0).getString("text")).as("Should greet by name").contains("Hello, WildFly!");
    }

    @Test
    public void testElicitationDeclineRoundTrip() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"greet-with-name","arguments":{}}}"""
                .formatted(toolCallId);

        postToStreamable(toolCallMessage);

        String elicitationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should be elicitation/create").contains("elicitation/create");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"decline"}}"""
                .formatted(elicitationId);
        postToStreamable(clientResponse);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after decline").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should indicate declined").contains("declined");
    }

    @Test
    public void testElicitationWithRegularArgsAndConfirmation() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"add-with-confirmation","arguments":{"a":"5","b":"3"}}}"""
                .formatted(toolCallId);

        postToStreamable(toolCallMessage);

        String elicitationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should ask for confirmation").contains("Confirm");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"accept","content":{"confirm":true}}}"""
                .formatted(elicitationId);
        postToStreamable(clientResponse);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should contain sum result").contains("8");
    }

    // ==================== URL Mode Elicitation ====================

    @Test
    public void testUrlElicitationAcceptRoundTrip() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"authenticate-via-url","arguments":{}}}"""
                .formatted(toolCallId);

        postToStreamable(toolCallMessage);

        String elicitationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should be an elicitation/create request").contains("elicitation/create");
        assertThat(elicitationJson).as("Should contain mode url").contains("\"mode\":\"url\"");
        assertThat(elicitationJson).as("Should contain the URL").contains("https://example.com/oauth/authorize");
        assertThat(elicitationJson).as("Should contain the elicitationId").contains("auth-integration-test");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"accept"}}"""
                .formatted(elicitationId);
        int statusCode = postToStreamable(clientResponse);
        assertThat(statusCode).as("Client response POST should succeed").isEqualTo(200);

        // The tool is now blocked, waiting for the out-of-band interaction to complete.
        // Simulate the OAuth callback by hitting the REST endpoint — this is what
        // the user's browser would do after completing the auth flow.
        URL callbackUrl = deploymentUrl.toURI().resolve("api/oauth/callback/auth-integration-test").toURL();
        HttpURLConnection callbackConn = (HttpURLConnection) callbackUrl.openConnection();
        callbackConn.setRequestMethod("GET");
        assertThat(callbackConn.getResponseCode()).as("OAuth callback should succeed").isEqualTo(200);
        callbackConn.disconnect();

        // The callback unblocks the tool, which sends notifications/elicitation/complete
        String notificationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notificationJson).as("Should receive elicitation complete notification").isNotNull();
        assertThat(notificationJson).as("Should be notifications/elicitation/complete")
                .contains("notifications/elicitation/complete");
        assertThat(notificationJson).as("Should contain the elicitationId").contains("auth-integration-test");

        // Then the tool returns its result
        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after URL elicitation").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();

        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should indicate success")
                .contains("Authentication successful");
    }

    @Test
    public void testUrlElicitationDeclineRoundTrip() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"authenticate-via-url","arguments":{}}}"""
                .formatted(toolCallId);

        postToStreamable(toolCallMessage);

        String elicitationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should contain mode url").contains("\"mode\":\"url\"");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"decline"}}"""
                .formatted(elicitationId);
        postToStreamable(clientResponse);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after decline").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should indicate declined")
                .contains("Authentication declined");
    }
}
