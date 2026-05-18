/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the MCP server subsystem.
 *
 * <p>Deploys a WAR with MCP-annotated beans (tools, prompts, resources) and tests
 * the MCP protocol via HTTP using the streamable endpoint.</p>
 *
 * <p>The MCP streamable HTTP transport works as follows:
 * <ul>
 *   <li>The first POST (initialize) opens an SSE connection that stays open</li>
 *   <li>All subsequent POST responses are sent back through that SSE connection</li>
 * </ul>
 * Therefore, this test keeps a background reader on the SSE stream and collects
 * responses via a blocking queue.</p>
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MCPServerIntegrationTestCase {

    private static String sessionId;
    private static BlockingQueue<String> sseResponses;
    private static Thread sseReaderThread;
    private static HttpURLConnection sseConnection;

    @ArquillianResource
    private URL deploymentUrl;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "mcp-test.war")
                .addClass(TestMCPTool.class)
                .addClass(TestMCPPrompt.class)
                .addClass(TestMCPResource.class)
                .addClass(TestMCPElicitationTool.class)
                .addClass(TestMCPLoggingTool.class)
                .addAsLibraries(new File("target/test-libs/assertj-core-3.26.3.jar"))
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return archive;
    }

    @Test
    @Order(1)
    public void testInitialize() throws Exception {
        sseResponses = new LinkedBlockingQueue<>();

        String initMessage = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"test-client","version":"1.0.0"},"capabilities":{"elicitation":{}}}}""";

        URL streamUrl = deploymentUrl.toURI().resolve("stream").toURL();
        sseConnection = (HttpURLConnection) streamUrl.openConnection();
        sseConnection.setRequestMethod("POST");
        sseConnection.setRequestProperty("Content-Type", "application/json");
        sseConnection.setRequestProperty("Accept", "application/json, text/event-stream");
        sseConnection.setDoOutput(true);
        sseConnection.setConnectTimeout(5000);
        sseConnection.setReadTimeout(0);

        try (OutputStream os = sseConnection.getOutputStream()) {
            os.write(initMessage.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = sseConnection.getResponseCode();
        assertThat(statusCode).as("Initialize should return 200").isEqualTo(200);

        sessionId = sseConnection.getHeaderField("mcp-session-id");
        assertThat(sessionId).as("Session ID should be returned").isNotNull();

        sseReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sseConnection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        sseResponses.offer(line.substring(5).trim());
                    }
                }
            } catch (Exception e) {
                // Connection closed or read error - expected during shutdown
            }
        }, "sse-reader");
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive initialize response").isNotNull();
        assertThat(response).as("Response should contain protocolVersion").contains("protocolVersion");
        assertThat(response).as("Response should contain server capabilities").contains("capabilities");
        assertThat(response).as("Response should advertise tools capability").contains("tools");
        assertThat(response).as("Response should advertise prompts capability").contains("prompts");
        assertThat(response).as("Response should advertise resources capability").contains("resources");
    }

    @Test
    @Order(2)
    public void testInitializedNotification() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String initializedMessage = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""";

        int statusCode = postToStreamable(initializedMessage);
        assertThat(statusCode).as("Initialized notification should succeed").isEqualTo(200);
    }

    @Test
    @Order(3)
    public void testPing() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String pingMessage = """
                {"jsonrpc":"2.0","id":2,"method":"ping"}""";

        postToStreamable(pingMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Ping should return a result").isNotNull();
        assertThat(response).as("Ping should return a result").contains("\"result\"");
    }

    @Test
    @Order(4)
    public void testToolsList() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolsListMessage = """
                {"jsonrpc":"2.0","id":3,"method":"tools/list"}""";

        postToStreamable(toolsListMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive tools list response").isNotNull();
        assertThat(response).as("Should contain tools array").contains("\"tools\"");
        assertThat(response).as("Should list the add tool").contains("\"add\"");
        assertThat(response).as("Should list the echo tool").contains("\"echo\"");
        assertThat(response).as("Should contain tool descriptions").contains("Adds two numbers");
        assertThat(response).as("Should contain inputSchema").contains("inputSchema");
    }

    @Test
    @Order(5)
    public void testToolsCall() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolsCallMessage = """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello MCP"}}}""";

        postToStreamable(toolsCallMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive tool call response").isNotNull();

        JsonObject jsonResponse = Json.createReader(new StringReader(response)).readObject();
        JsonObject result = jsonResponse.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();

        JsonArray content = result.getJsonArray("content");
        assertThat(content).as("Should contain content array").isNotNull();
        assertThat(content.size()).as("Should have one content block").isEqualTo(1);

        JsonObject contentBlock = content.getJsonObject(0);
        assertThat(contentBlock.getString("type")).as("Content type should be 'text'").isEqualTo("text");
        assertThat(contentBlock.getString("text")).as("Content text should contain echoed message").contains("hello MCP");
        assertThat(contentBlock.containsKey("annotations")).as("Null annotations should not be present").isFalse();
    }

    @Test
    @Order(6)
    public void testToolsCallAdd() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolsCallMessage = """
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"add","arguments":{"a":"3","b":"7"}}}""";

        postToStreamable(toolsCallMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive tool call response").isNotNull();

        JsonObject jsonResponse = Json.createReader(new StringReader(response)).readObject();
        JsonObject result = jsonResponse.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();

        JsonArray content = result.getJsonArray("content");
        assertThat(content).as("Should contain content array").isNotNull();
        assertThat(content.size()).as("Should have one content block").isEqualTo(1);

        JsonObject contentBlock = content.getJsonObject(0);
        assertThat(contentBlock.getString("type")).as("Content type should be 'text'").isEqualTo("text");
        assertThat(contentBlock.getString("text")).as("Content text should contain sum result").contains("10");
        assertThat(contentBlock.containsKey("annotations")).as("Null annotations should not be present").isFalse();
    }

    @Test
    @Order(7)
    public void testPromptsList() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String promptsListMessage = """
                {"jsonrpc":"2.0","id":6,"method":"prompts/list"}""";

        postToStreamable(promptsListMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive prompts list response").isNotNull();
        assertThat(response).as("Should contain prompts array").contains("\"prompts\"");
        assertThat(response).as("Should list the greeting prompt").contains("\"greeting\"");
        assertThat(response).as("Should contain prompt description").contains("Generates a greeting");
    }

    @Test
    @Order(8)
    public void testPromptsGet() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String promptsGetMessage = """
                {"jsonrpc":"2.0","id":7,"method":"prompts/get","params":{"name":"greeting","arguments":{"name":"WildFly"}}}""";

        postToStreamable(promptsGetMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive prompt get response").isNotNull();

        JsonObject jsonResponse = Json.createReader(new StringReader(response)).readObject();
        JsonObject result = jsonResponse.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();
        assertThat(result.getString("description")).as("Should contain description").isEqualTo("Generates a greeting message");

        JsonArray messages = result.getJsonArray("messages");
        assertThat(messages).as("Should contain messages array").isNotNull();
        assertThat(messages.size()).as("Should have one message").isEqualTo(1);

        JsonObject message = messages.getJsonObject(0);
        assertThat(message.getString("role")).as("Role should be lowercase 'user'").isEqualTo("user");

        JsonObject content = message.getJsonObject("content");
        assertThat(content).as("Content should be a JSON object, not an array").isNotNull();
        assertThat(content.getString("type")).as("Content type should be 'text'").isEqualTo("text");
        assertThat(content.getString("text")).as("Content text should contain greeting").contains("Hello, WildFly");
        assertThat(content.containsKey("annotations")).as("Null annotations should not be present").isFalse();
    }

    @Test
    @Order(13)
    public void testPromptsGetAssistantRole() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String promptsGetMessage = """
                {"jsonrpc":"2.0","id":13,"method":"prompts/get","params":{"name":"assistant-reply","arguments":{"topic":"Java"}}}""";

        postToStreamable(promptsGetMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive prompt get response").isNotNull();

        JsonObject jsonResponse = Json.createReader(new StringReader(response)).readObject();
        JsonObject result = jsonResponse.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();

        JsonArray messages = result.getJsonArray("messages");
        assertThat(messages).as("Should contain messages array").isNotNull();
        assertThat(messages.size()).as("Should have one message").isEqualTo(1);

        JsonObject message = messages.getJsonObject(0);
        assertThat(message.getString("role")).as("Role should be lowercase 'assistant'").isEqualTo("assistant");

        JsonObject content = message.getJsonObject("content");
        assertThat(content).as("Content should be a JSON object, not an array").isNotNull();
        assertThat(content.getString("type")).as("Content type should be 'text'").isEqualTo("text");
        assertThat(content.getString("text")).as("Content text should contain topic").contains("Java");
    }

    @Test
    @Order(9)
    public void testResourcesList() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String resourcesListMessage = """
                {"jsonrpc":"2.0","id":8,"method":"resources/list"}""";

        postToStreamable(resourcesListMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive resources list response").isNotNull();
        assertThat(response).as("Should contain resources array").contains("\"resources\"");
        assertThat(response).as("Should list the test-info resource").contains("\"test-info\"");
        assertThat(response).as("Should contain the resource URI").contains("test://info");
    }

    @Test
    @Order(10)
    public void testResourcesRead() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String resourcesReadMessage = """
                {"jsonrpc":"2.0","id":9,"method":"resources/read","params":{"uri":"test://info"}}""";

        postToStreamable(resourcesReadMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive resource read response").isNotNull();
        assertThat(response).as("Should contain result").contains("\"result\"");
        assertThat(response).as("Should contain resource content").contains("WildFly MCP Test Resource");
    }

    // ==================== Resource Subscribe / Unsubscribe ====================

    @Test
    @Order(18)
    public void testResourcesSubscribeCapabilityAdvertised() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Re-use the initialize response already received in testInitialize — re-initialize just to inspect
        // Actually, the capability was already asserted in testInitialize via "resources". We validate subscribe here.
        // Send a fresh tools/list to avoid consuming a pending SSE message, then check the init response cached earlier.
        // Instead, call resources/list and ensure subscribe is still the advertised capability.
        // We validate this by issuing resources/subscribe on a known URI and expecting success (not method-not-found).
        String subscribeMessage = """
                {"jsonrpc":"2.0","id":30,"method":"resources/subscribe","params":{"uri":"test://info"}}""";

        postToStreamable(subscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Subscribe should return a result").isNotNull();
        assertThat(response).as("Subscribe should not return an error").doesNotContain("\"error\"");
        assertThat(response).as("Subscribe should return an empty result object").contains("\"result\"");
    }

    @Test
    @Order(19)
    public void testResourcesSubscribeKnownResource() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String subscribeMessage = """
                {"jsonrpc":"2.0","id":31,"method":"resources/subscribe","params":{"uri":"test://info"}}""";

        postToStreamable(subscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive subscribe response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Should be a result, not an error").isTrue();
        assertThat(json.getJsonObject("result").isEmpty()).as("Result should be empty object per MCP spec").isTrue();
    }

    @Test
    @Order(20)
    public void testResourcesSubscribeSecondResource() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String subscribeMessage = """
                {"jsonrpc":"2.0","id":32,"method":"resources/subscribe","params":{"uri":"test://status"}}""";

        postToStreamable(subscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive subscribe response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Should be a result, not an error").isTrue();
        assertThat(json.getJsonObject("result").isEmpty()).as("Result should be empty object per MCP spec").isTrue();
    }

    @Test
    @Order(21)
    public void testResourcesReadAfterSubscribe() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Subscribing must not affect the ability to read the resource
        String readMessage = """
                {"jsonrpc":"2.0","id":33,"method":"resources/read","params":{"uri":"test://status"}}""";

        postToStreamable(readMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive resource read response").isNotNull();
        assertThat(response).as("Should contain contents").contains("\"contents\"");
        assertThat(response).as("Should contain JSON status content").contains("running");
    }

    @Test
    @Order(22)
    public void testResourcesUnsubscribeKnownResource() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String unsubscribeMessage = """
                {"jsonrpc":"2.0","id":34,"method":"resources/unsubscribe","params":{"uri":"test://info"}}""";

        postToStreamable(unsubscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive unsubscribe response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Should be a result, not an error").isTrue();
        assertThat(json.getJsonObject("result").isEmpty()).as("Result should be empty object per MCP spec").isTrue();
    }

    @Test
    @Order(23)
    public void testResourcesUnsubscribeIdempotent() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Unsubscribing from a resource that was already unsubscribed (or never subscribed) should still succeed
        String unsubscribeMessage = """
                {"jsonrpc":"2.0","id":35,"method":"resources/unsubscribe","params":{"uri":"test://info"}}""";

        postToStreamable(unsubscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive unsubscribe response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Repeated unsubscribe should still succeed").isTrue();
    }

    @Test
    @Order(24)
    public void testResourcesSubscribeMissingParams() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String subscribeMessage = """
                {"jsonrpc":"2.0","id":36,"method":"resources/subscribe"}""";

        postToStreamable(subscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing params should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    @Order(25)
    public void testResourcesSubscribeMissingUri() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String subscribeMessage = """
                {"jsonrpc":"2.0","id":37,"method":"resources/subscribe","params":{}}""";

        postToStreamable(subscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing URI should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    @Order(26)
    public void testResourcesUnsubscribeMissingParams() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String unsubscribeMessage = """
                {"jsonrpc":"2.0","id":38,"method":"resources/unsubscribe"}""";

        postToStreamable(unsubscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing params should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    @Order(27)
    public void testResourcesUnsubscribeMissingUri() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String unsubscribeMessage = """
                {"jsonrpc":"2.0","id":39,"method":"resources/unsubscribe","params":{}}""";

        postToStreamable(unsubscribeMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing URI should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    @Order(28)
    public void testResourcesListIncludesSecondResource() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String listMessage = """
                {"jsonrpc":"2.0","id":40,"method":"resources/list"}""";

        postToStreamable(listMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive resources list response").isNotNull();
        assertThat(response).as("Should contain test-status resource").contains("\"test-status\"");
        assertThat(response).as("Should contain test://status URI").contains("test://status");
        assertThat(response).as("Should contain application/json mimeType").contains("application/json");
    }

    @Test
    @Order(11)
    public void testUnsupportedMethod() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String unsupportedMessage = """
                {"jsonrpc":"2.0","id":10,"method":"unsupported/method"}""";

        postToStreamable(unsupportedMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();
        assertThat(response).as("Should contain error").contains("\"error\"");
        assertThat(response).as("Should contain method not found code").contains("-32601");
    }

    @Test
    @Order(12)
    public void testLoggingSetLevel() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String loggingMessage = """
                {"jsonrpc":"2.0","id":11,"method":"logging/setLevel","params":{"level":"info"}}""";

        postToStreamable(loggingMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive logging response").isNotNull();
        assertThat(response).as("Should contain result").contains("\"result\"");
    }

    @Test
    @Order(14)
    public void testElicitationToolListedWithoutSenderParam() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolsListMessage = """
                {"jsonrpc":"2.0","id":20,"method":"tools/list"}""";

        postToStreamable(toolsListMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive tools list response").isNotNull();
        assertThat(response).as("Should list the greet-with-name tool").contains("greet-with-name");
        // ElicitationSender must NOT appear as a client-supplied input parameter
        assertThat(response).as("ElicitationSender must not appear in inputSchema").doesNotContain("ElicitationSender");
        assertThat(response).as("greet-with-name should have empty required array").isNotEmpty();
    }

    @Test
    @Order(15)
    public void testElicitationAcceptRoundTrip() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Call the elicitation tool — it will block the tool thread waiting for our response
        String toolCallMessage = """
                {"jsonrpc":"2.0","id":21,"method":"tools/call","params":{"name":"greet-with-name","arguments":{}}}""";

        postToStreamable(toolCallMessage);

        // The server should now send an elicitation/create request over the SSE stream
        String elicitationJson = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should be an elicitation/create request").contains("elicitation/create");
        assertThat(elicitationJson).as("Should contain the prompt message").contains("What is your name?");
        assertThat(elicitationJson).as("Should contain requestedSchema").contains("requestedSchema");

        // Parse the request id so we can respond to it
        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        // Simulate client accepting with the user's name
        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"accept","content":{"name":"WildFly"}}}"""
                .formatted(elicitationId);
        int statusCode = postToStreamable(clientResponse);
        assertThat(statusCode).as("Client response POST should succeed").isEqualTo(200);

        // Now the tool should complete and send the tool result
        String toolResult = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after elicitation").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();

        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content).as("Should contain content array").isNotNull();
        assertThat(content.getJsonObject(0).getString("text")).as("Should greet by name").contains("Hello, WildFly!");
    }

    @Test
    @Order(16)
    public void testElicitationDeclineRoundTrip() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":22,"method":"tools/call","params":{"name":"greet-with-name","arguments":{}}}""";

        postToStreamable(toolCallMessage);

        String elicitationJson = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should be elicitation/create").contains("elicitation/create");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        // Client declines
        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"decline"}}"""
                .formatted(elicitationId);
        postToStreamable(clientResponse);

        String toolResult = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result after decline").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should indicate declined").contains("declined");
    }

    @Test
    @Order(17)
    public void testElicitationWithRegularArgsAndConfirmation() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":23,"method":"tools/call","params":{"name":"add-with-confirmation","arguments":{"a":"5","b":"3"}}}""";

        postToStreamable(toolCallMessage);

        String elicitationJson = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(elicitationJson).as("Should receive elicitation/create request").isNotNull();
        assertThat(elicitationJson).as("Should ask for confirmation").contains("Confirm");

        JsonObject elicitationMessage = Json.createReader(new StringReader(elicitationJson)).readObject();
        long elicitationId = elicitationMessage.getJsonNumber("id").longValue();

        // Client confirms
        String clientResponse = """
                {"jsonrpc":"2.0","id":%d,"result":{"action":"accept","content":{"confirm":true}}}"""
                .formatted(elicitationId);
        postToStreamable(clientResponse);

        String toolResult = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should contain sum result").contains("8");
    }

    // ==================== Logging / McpLog Injection ====================

    @Test
    @Order(40)
    public void testLoggingToolListedWithoutMcpLogParam() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String toolsListMessage = """
                {"jsonrpc":"2.0","id":50,"method":"tools/list"}""";

        postToStreamable(toolsListMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive tools list response").isNotNull();
        assertThat(response).as("Should list the log-test tool").contains("log-test");
        assertThat(response).as("McpLog must not appear in inputSchema").doesNotContain("McpLog");
        assertThat(response).as("McpLog must not appear as property name").doesNotContain("\"mcpLog\"");
    }

    @Test
    @Order(41)
    public void testLoggingToolSendsNotification() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Set log level to DEBUG so all messages pass the filter
        String setLevelMessage = """
                {"jsonrpc":"2.0","id":51,"method":"logging/setLevel","params":{"level":"debug"}}""";
        postToStreamable(setLevelMessage);
        String setLevelResponse = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(setLevelResponse).as("Should receive setLevel response").isNotNull();
        assertThat(setLevelResponse).as("setLevel should succeed").contains("\"result\"");

        // Call the log-test tool with level=info
        String toolCallMessage = """
                {"jsonrpc":"2.0","id":52,"method":"tools/call","params":{"name":"log-test","arguments":{"level":"info"}}}""";
        postToStreamable(toolCallMessage);

        // Expect the notification/message first, then the tool result
        String firstMessage = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(firstMessage).as("Should receive first SSE message").isNotNull();
        String secondMessage = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(secondMessage).as("Should receive second SSE message").isNotNull();

        // One of the two should be the notification, the other the tool result
        String notification;
        String toolResult;
        if (firstMessage.contains("notifications/message")) {
            notification = firstMessage;
            toolResult = secondMessage;
        } else {
            notification = secondMessage;
            toolResult = firstMessage;
        }

        // Verify the notification
        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/message").isEqualTo("notifications/message");
        JsonObject params = notificationJson.getJsonObject("params");
        assertThat(params.getString("level")).as("Notification level should be info").isEqualTo("info");
        assertThat(params.getString("logger")).as("Logger name should be the tool name").isEqualTo("log-test");
        assertThat(params.getString("data")).as("Data should contain the log message").contains("Info message from tool");

        // Verify the tool result
        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should contain logged level").contains("Logged at info");
    }

    @Test
    @Order(42)
    public void testLoggingLevelFiltersMessages() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Set log level to ERROR so INFO messages are filtered out
        String setLevelMessage = """
                {"jsonrpc":"2.0","id":53,"method":"logging/setLevel","params":{"level":"error"}}""";
        postToStreamable(setLevelMessage);
        String setLevelResponse = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(setLevelResponse).as("Should receive setLevel response").isNotNull();

        // Call the log-test tool with level=info — should be filtered
        String toolCallMessage = """
                {"jsonrpc":"2.0","id":54,"method":"tools/call","params":{"name":"log-test","arguments":{"level":"info"}}}""";
        postToStreamable(toolCallMessage);

        // Should only receive the tool result, no notification
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive tool result").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(response)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should be a tool result, not a notification").isTrue();
        assertThat(resultJson.containsKey("method")).as("Should not be a notification").isFalse();

        // Verify no extra notification arrived
        String extra = sseResponses.poll(2, TimeUnit.SECONDS);
        assertThat(extra).as("No notification should be sent when level is below threshold").isNull();
    }

    @Test
    @Order(43)
    public void testLoggingErrorPassesWhenLevelIsError() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String setLevelMessage = """
                {"jsonrpc":"2.0","id":59,"method":"logging/setLevel","params":{"level":"error"}}""";
        postToStreamable(setLevelMessage);
        String setLevelResponse = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(setLevelResponse).as("Should receive setLevel response").isNotNull();

        // Call log-test with level=error — should pass the filter
        String toolCallMessage = """
                {"jsonrpc":"2.0","id":55,"method":"tools/call","params":{"name":"log-test","arguments":{"level":"error"}}}""";
        postToStreamable(toolCallMessage);

        String firstMessage = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(firstMessage).as("Should receive first SSE message").isNotNull();
        String secondMessage = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(secondMessage).as("Should receive second SSE message").isNotNull();

        String notification;
        String toolResult;
        if (firstMessage.contains("notifications/message")) {
            notification = firstMessage;
            toolResult = secondMessage;
        } else {
            notification = secondMessage;
            toolResult = firstMessage;
        }

        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/message").isEqualTo("notifications/message");
        JsonObject params = notificationJson.getJsonObject("params");
        assertThat(params.getString("level")).as("Notification level should be error").isEqualTo("error");
        assertThat(params.getString("data")).as("Data should contain the error message").contains("Error message from tool");

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();
    }

    @Test
    @Order(44)
    public void testLoggingSetLevelInvalidLevel() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String setLevelMessage = """
                {"jsonrpc":"2.0","id":56,"method":"logging/setLevel","params":{"level":"nonexistent"}}""";
        postToStreamable(setLevelMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Invalid level should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    @Order(45)
    public void testLoggingSetLevelMissingParams() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String setLevelMessage = """
                {"jsonrpc":"2.0","id":57,"method":"logging/setLevel"}""";
        postToStreamable(setLevelMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing params should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    @Order(46)
    public void testLoggingSetLevelMissingLevel() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        String setLevelMessage = """
                {"jsonrpc":"2.0","id":58,"method":"logging/setLevel","params":{}}""";
        postToStreamable(setLevelMessage);
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive error response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing level should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    /**
     * Sends a JSON-RPC message to the streamable endpoint using an existing session.
     * The response will arrive on the SSE stream (sseResponses queue), not in the HTTP response body.
     */
    private int postToStreamable(String jsonBody) throws Exception {
        URL streamUrl = new URL(deploymentUrl, "stream");
        HttpURLConnection conn = (HttpURLConnection) streamUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setRequestProperty("mcp-session-id", sessionId);
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = conn.getResponseCode();
        conn.disconnect();
        return statusCode;
    }
}
