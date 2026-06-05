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

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for MCP elicitation (form mode and URL mode).
 */
public class ElicitationIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-elicitation.war")
                .addClass(TestMCPElicitationTool.class)
                .addClass(TestThirdPartyApplication.class)
                .addClass(TestThirdPartyApplication.TestCallbackEndpoint.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

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

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        assertThat(elicitationMessage.getString("method")).as("Should be elicitation/create").isEqualTo("elicitation/create");

        JsonObject elicitationParams = elicitationMessage.getJsonObject("params");
        assertThat(elicitationParams.getString("mode")).as("Mode should be url").isEqualTo("url");
        String elicitationUrl = elicitationParams.getString("url");
        assertThat(elicitationUrl).as("Should contain the authorization URL").isEqualTo("/my-app/callback");
        String toolElicitationId = elicitationParams.getString("elicitationId");
        assertThat(toolElicitationId).as("Should start with known prefix").startsWith("auth-integration-test");
        assertThat(elicitationParams.getString("message")).as("Should contain the prompt message").isEqualTo("Please authenticate with your identity provider");

        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"accept"}}"""
                .formatted(elicitationId);
        int statusCode = postToStreamable(clientResponse);
        assertThat(statusCode).as("Client response POST should succeed").isEqualTo(200);

        String relativePath = elicitationUrl.startsWith("/") ? elicitationUrl.substring(1) : elicitationUrl;
        URL callbackUrl = deploymentUrl.toURI().resolve("%s/%s".formatted(relativePath, toolElicitationId)).toURL();
        System.out.println(">>> callbackUrl = " + callbackUrl);
        HttpURLConnection callbackConn = (HttpURLConnection) callbackUrl.openConnection();
        callbackConn.setRequestMethod("GET");
        assertThat(callbackConn.getResponseCode()).as("callback should succeed").isEqualTo(200);
        callbackConn.disconnect();

        String notificationJson = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notificationJson).as("Should receive elicitation complete notification").isNotNull();

        JsonObject notificationMessage = Json.createReader(new StringReader(notificationJson)).readObject();
        assertThat(notificationMessage.getString("method")).as("Should be notifications/elicitation/complete")
                .isEqualTo("notifications/elicitation/complete");
        assertThat(notificationMessage.getJsonObject("params").getString("elicitationId"))
                .as("Notification should reference the correct elicitationId").isEqualTo(toolElicitationId);

        // Then the tool returns its result
        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after URL elicitation").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();

        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should indicate success")
                .contains("Tool finished");
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
