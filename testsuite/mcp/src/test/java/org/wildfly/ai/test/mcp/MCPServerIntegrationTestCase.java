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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
 * Therefore, this test keeps a background reader on the SSE stream and dispatches
 * responses to the correct test via JSON-RPC id correlation.</p>
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MCPServerIntegrationTestCase {

    private static final long RESPONSE_TIMEOUT_SECONDS = 10;
    private static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    private static final String PROGRESS_TOKEN = "progressToken";

    private volatile boolean initialized;
    private String sessionId;
    private Thread sseReaderThread;
    private HttpURLConnection sseConnection;

    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();
    private final BlockingQueue<String> serverInitiatedMessages = new LinkedBlockingQueue<>();

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
                .addClass(TestMCPProgressTool.class)
                .addAsLibraries(new File("target/test-libs/assertj-core-3.26.3.jar"))
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
        return archive;
    }

    @AfterEach
    public void cleanUpState() throws Exception {
        pendingResponses.values().forEach(f -> f.cancel(true));
        pendingResponses.clear();
        int staleCount = serverInitiatedMessages.size();
        if (staleCount > 0) {
            System.err.println("[WARN] " + staleCount + " stale server-initiated message(s) drained after test");
        }
        serverInitiatedMessages.clear();

        if (initialized) {
            sendAndReceive("logging/setLevel", Json.createObjectBuilder()
                    .add("level", "warning")
                    .build());
        }
    }

    @AfterAll
    public void tearDown() {
        if (sseReaderThread != null) {
            sseReaderThread.interrupt();
        }
        if (sseConnection != null) {
            sseConnection.disconnect();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        if (initialized) {
            return;
        }
        initialized = true;

        long initId = nextId.getAndIncrement();
        CompletableFuture<String> initFuture = new CompletableFuture<>();
        pendingResponses.put(initId, initFuture);

        String initMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"test-client","version":"1.0.0"},"capabilities":{"elicitation":{}}}}"""
                .formatted(initId);

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
                        String data = line.substring(5).trim();
                        dispatchSseEvent(data);
                    }
                }
            } catch (Exception e) {
                // Connection closed or read error - expected during shutdown
            }
        }, "sse-reader");
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        String response = initFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(response).as("Should receive initialize response").isNotNull();
        assertThat(response).as("Response should contain protocolVersion").contains("protocolVersion");
        assertThat(response).as("Response should contain server capabilities").contains("capabilities");
        assertThat(response).as("Response should advertise tools capability").contains("tools");
        assertThat(response).as("Response should advertise prompts capability").contains("prompts");
        assertThat(response).as("Response should advertise resources capability").contains("resources");

        String initializedMessage = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""";

        int notifStatusCode = postToStreamable(initializedMessage);
        assertThat(notifStatusCode).as("Initialized notification should succeed").isEqualTo(200);
    }

    @Test
    public void testPing() throws Exception {
        String response = sendAndReceive("ping", null);
        assertThat(response).as("Ping should return a result").contains("\"result\"");
    }

    @Test
    public void testToolsList() throws Exception {
        String response = sendAndReceive("tools/list", null);
        assertThat(response).as("Should contain tools array").contains("\"tools\"");
        assertThat(response).as("Should list the add tool").contains("\"add\"");
        assertThat(response).as("Should list the echo tool").contains("\"echo\"");
        assertThat(response).as("Should contain tool descriptions").contains("Adds two numbers");
        assertThat(response).as("Should contain inputSchema").contains("inputSchema");
    }

    @Test
    public void testToolsCall() throws Exception {
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "echo")
                .add("arguments", Json.createObjectBuilder().add("message", "hello MCP"))
                .build());

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
    public void testToolsCallAdd() throws Exception {
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "add")
                .add("arguments", Json.createObjectBuilder().add("a", "3").add("b", "7"))
                .build());

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
    public void testPromptsList() throws Exception {
        String response = sendAndReceive("prompts/list", null);
        assertThat(response).as("Should contain prompts array").contains("\"prompts\"");
        assertThat(response).as("Should list the greeting prompt").contains("\"greeting\"");
        assertThat(response).as("Should contain prompt description").contains("Generates a greeting");
    }

    @Test
    public void testPromptsGet() throws Exception {
        String response = sendAndReceive("prompts/get", Json.createObjectBuilder()
                .add("name", "greeting")
                .add("arguments", Json.createObjectBuilder().add("name", "WildFly"))
                .build());

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
    public void testPromptsGetAssistantRole() throws Exception {
        String response = sendAndReceive("prompts/get", Json.createObjectBuilder()
                .add("name", "assistant-reply")
                .add("arguments", Json.createObjectBuilder().add("topic", "Java"))
                .build());

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
    public void testResourcesList() throws Exception {
        String response = sendAndReceive("resources/list", null);
        assertThat(response).as("Should contain resources array").contains("\"resources\"");
        assertThat(response).as("Should list the test-info resource").contains("\"test-info\"");
        assertThat(response).as("Should contain the resource URI").contains("test://info");
        assertThat(response).as("Should contain test-status resource").contains("\"test-status\"");
        assertThat(response).as("Should contain test://status URI").contains("test://status");
        assertThat(response).as("Should contain application/json mimeType").contains("application/json");
    }

    @Test
    public void testResourcesRead() throws Exception {
        String response = sendAndReceive("resources/read", Json.createObjectBuilder()
                .add("uri", "test://info")
                .build());

        assertThat(response).as("Should contain result").contains("\"result\"");
        assertThat(response).as("Should contain resource content").contains("WildFly MCP Test Resource");
    }

    // ==================== Resource Subscribe / Unsubscribe ====================

    @Test
    public void testResourcesSubscribeKnownResource() throws Exception {
        String response = sendAndReceive("resources/subscribe", Json.createObjectBuilder()
                .add("uri", "test://info")
                .build());

        assertThat(response).as("Subscribe should not return an error").doesNotContain("\"error\"");
        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Should be a result, not an error").isTrue();
        assertThat(json.getJsonObject("result").isEmpty()).as("Result should be empty object per MCP spec").isTrue();
    }

    @Test
    public void testResourcesSubscribeSecondResource() throws Exception {
        String response = sendAndReceive("resources/subscribe", Json.createObjectBuilder()
                .add("uri", "test://status")
                .build());

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Should be a result, not an error").isTrue();
        assertThat(json.getJsonObject("result").isEmpty()).as("Result should be empty object per MCP spec").isTrue();
    }

    @Test
    public void testResourcesReadAfterSubscribe() throws Exception {
        JsonObject statusUri = Json.createObjectBuilder()
                .add("uri", "test://status")
                .build();

        // Subscribe first, then read
        String subscribeResponse = sendAndReceive("resources/subscribe", statusUri);
        assertThat(subscribeResponse).as("Subscribe should succeed before read").doesNotContain("\"error\"");

        String response = sendAndReceive("resources/read", statusUri);

        assertThat(response).as("Should contain contents").contains("\"contents\"");
        assertThat(response).as("Should contain JSON status content").contains("running");
    }

    @Test
    public void testResourcesUnsubscribeKnownResource() throws Exception {
        JsonObject infoUri = Json.createObjectBuilder()
                .add("uri", "test://info")
                .build();

        // Subscribe first so we can unsubscribe
        String subscribeResponse = sendAndReceive("resources/subscribe", infoUri);
        assertThat(subscribeResponse).as("Subscribe should succeed before unsubscribe").doesNotContain("\"error\"");

        String response = sendAndReceive("resources/unsubscribe", infoUri);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Should be a result, not an error").isTrue();
        assertThat(json.getJsonObject("result").isEmpty()).as("Result should be empty object per MCP spec").isTrue();
    }

    @Test
    public void testResourcesUnsubscribeIdempotent() throws Exception {
        // Unsubscribing from a resource that was never subscribed should still succeed
        String response = sendAndReceive("resources/unsubscribe", Json.createObjectBuilder()
                .add("uri", "test://info")
                .build());

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("result")).as("Repeated unsubscribe should still succeed").isTrue();
    }

    @Test
    public void testResourcesSubscribeMissingParams() throws Exception {
        String response = sendAndReceive("resources/subscribe", null);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing params should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    public void testResourcesSubscribeMissingUri() throws Exception {
        String response = sendAndReceive("resources/subscribe", Json.createObjectBuilder().build());

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing URI should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    public void testResourcesUnsubscribeMissingParams() throws Exception {
        String response = sendAndReceive("resources/unsubscribe", null);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing params should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    public void testResourcesUnsubscribeMissingUri() throws Exception {
        String response = sendAndReceive("resources/unsubscribe", Json.createObjectBuilder().build());

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing URI should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    public void testUnsupportedMethod() throws Exception {
        String response = sendAndReceive("unsupported/method", null);
        assertThat(response).as("Should contain error").contains("\"error\"");
        assertThat(response).as("Should contain method not found code").contains("-32601");
    }

    @Test
    public void testLoggingSetLevel() throws Exception {
        String response = sendAndReceive("logging/setLevel", Json.createObjectBuilder()
                .add("level", "info")
                .build());
        assertThat(response).as("Should contain result").contains("\"result\"");
    }

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

    // ==================== Progress Notifications ====================

    @Test
    public void testProgressToolListedWithoutProgressParam() throws Exception {
        String response = sendAndReceive("tools/list", null);
        assertThat(response).as("Should list the progress-test tool").contains("progress-test");
        assertThat(response).as("Progress must not appear in inputSchema").doesNotContain("\"Progress\"");
        assertThat(response).as("Progress must not appear as property name").doesNotContain("\"progress\"");
    }

    @Test
    public void testProgressNotificationsSent() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"progress-test","arguments":{"steps":"3"},"_meta":{"progressToken":"test-token-1"}}}"""
                .formatted(toolCallId);
        postToStreamable(toolCallMessage);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        // Collect all progress notifications (3 steps expected)
        for (int i = 0; i < 3; i++) {
            String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(notification).as("Should receive progress notification " + (i + 1)).isNotNull();

            JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
            assertThat(notificationJson.getString("method")).as("Should be notifications/progress")
                    .isEqualTo(NOTIFICATIONS_PROGRESS);
            JsonObject params = notificationJson.getJsonObject("params");
            assertThat(params.getString(PROGRESS_TOKEN)).as("Token should match").isEqualTo("test-token-1");
            assertThat(params.getJsonNumber("total").intValue()).as("Total should be 3").isEqualTo(3);
            assertThat(params.getJsonNumber("progress").intValue()).as("Progress should be " + (i + 1))
                    .isEqualTo(i + 1);
        }

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should confirm completion").contains("Completed 3 steps");
    }

    @Test
    public void testProgressNotificationWithMessage() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"progress-notification-test","arguments":{},"_meta":{"progressToken":"msg-token"}}}"""
                .formatted(toolCallId);
        postToStreamable(toolCallMessage);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("Should receive progress notification").isNotNull();

        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/progress")
                .isEqualTo(NOTIFICATIONS_PROGRESS);
        JsonObject params = notificationJson.getJsonObject("params");
        assertThat(params.getString(PROGRESS_TOKEN)).as("Token should match").isEqualTo("msg-token");
        assertThat(params.getJsonNumber("progress").intValue()).as("Progress should be 50").isEqualTo(50);
        assertThat(params.getJsonNumber("total").intValue()).as("Total should be 100").isEqualTo(100);
        assertThat(params.getString("message")).as("Message should be present").isEqualTo("Halfway done");
    }

    @Test
    public void testProgressNoTokenDoesNotSendNotifications() throws Exception {
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "progress-no-token")
                .add("arguments", Json.createObjectBuilder())
                .build());

        JsonObject resultJson = Json.createReader(new StringReader(response)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should report no-token").isEqualTo("no-token");

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("No notifications should be sent without a token").isNull();
    }

    // ==================== MCP-Protocol-Version Header Validation ====================

    @Test
    public void testInvalidProtocolVersionHeaderReturns400() throws Exception {
        long id = nextId.getAndIncrement();
        String pingMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"ping"}""".formatted(id);

        int statusCode = postToStreamableWithProtocolVersion(pingMessage, "1999-01-01");
        assertThat(statusCode).as("Mismatched MCP-Protocol-Version should return 400").isEqualTo(400);
    }

    @Test
    public void testValidProtocolVersionHeaderSucceeds() throws Exception {
        long id = nextId.getAndIncrement();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingResponses.put(id, future);

        String pingMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"ping"}""".formatted(id);

        int statusCode = postToStreamableWithProtocolVersion(pingMessage, "2025-03-26");
        assertThat(statusCode).as("Matching MCP-Protocol-Version should succeed").isEqualTo(200);

        String response = future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(response).as("Ping should return a result").isNotNull();
        assertThat(response).as("Ping should contain result").contains("\"result\"");
    }

    @Test
    public void testAbsentProtocolVersionHeaderSucceeds() throws Exception {
        String response = sendAndReceive("ping", null);
        assertThat(response).as("Ping should return a result").contains("\"result\"");
    }

    // ==================== Logging / McpLog Injection ====================

    @Test
    public void testLoggingToolListedWithoutMcpLogParam() throws Exception {
        String response = sendAndReceive("tools/list", null);
        assertThat(response).as("Should list the log-test tool").contains("log-test");
        assertThat(response).as("McpLog must not appear in inputSchema").doesNotContain("McpLog");
        assertThat(response).as("McpLog must not appear as property name").doesNotContain("\"mcpLog\"");
    }

    @Test
    public void testLoggingToolSendsNotification() throws Exception {
        // Set log level to DEBUG so all messages pass the filter
        sendAndReceive("logging/setLevel", Json.createObjectBuilder()
                .add("level", "debug")
                .build());

        // Call the log-test tool — expect both a notification and a tool result
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"log-test","arguments":{"level":"info"}}}"""
                .formatted(toolCallId);
        postToStreamable(toolCallMessage);

        // The tool result arrives via the future (correlated by id)
        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        // The notification arrives via serverInitiatedMessages (no correlated id)
        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("Should receive logging notification").isNotNull();

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
    public void testLoggingLevelFiltersMessages() throws Exception {
        // Set log level to ERROR so INFO messages are filtered out
        sendAndReceive("logging/setLevel", Json.createObjectBuilder()
                .add("level", "error")
                .build());

        // Call the log-test tool with level=info — notification should be filtered
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "log-test")
                .add("arguments", Json.createObjectBuilder().add("level", "info"))
                .build());

        JsonObject resultJson = Json.createReader(new StringReader(response)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should be a tool result, not a notification").isTrue();
        assertThat(resultJson.containsKey("method")).as("Should not be a notification").isFalse();

        // Verify no notification arrived
        String extra = serverInitiatedMessages.poll(2, TimeUnit.SECONDS);
        assertThat(extra).as("No notification should be sent when level is below threshold").isNull();
    }

    @Test
    public void testLoggingErrorPassesWhenLevelIsError() throws Exception {
        sendAndReceive("logging/setLevel", Json.createObjectBuilder()
                .add("level", "error")
                .build());

        // Call log-test with level=error — should pass the filter
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"log-test","arguments":{"level":"error"}}}"""
                .formatted(toolCallId);
        postToStreamable(toolCallMessage);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("Should receive logging notification").isNotNull();

        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/message").isEqualTo("notifications/message");
        JsonObject params = notificationJson.getJsonObject("params");
        assertThat(params.getString("level")).as("Notification level should be error").isEqualTo("error");
        assertThat(params.getString("data")).as("Data should contain the error message").contains("Error message from tool");

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        assertThat(resultJson.containsKey("result")).as("Should contain result").isTrue();
    }

    @Test
    public void testLoggingSetLevelInvalidLevel() throws Exception {
        String response = sendAndReceive("logging/setLevel", Json.createObjectBuilder()
                .add("level", "nonexistent")
                .build());

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Invalid level should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    public void testLoggingSetLevelMissingParams() throws Exception {
        String response = sendAndReceive("logging/setLevel", null);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing params should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    @Test
    public void testLoggingSetLevelMissingLevel() throws Exception {
        String response = sendAndReceive("logging/setLevel", Json.createObjectBuilder().build());

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        assertThat(json.containsKey("error")).as("Missing level should return an error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).as("Should be INVALID_PARAMS (-32602)").isEqualTo(-32602);
    }

    /**
     * Dispatches an SSE event to the appropriate handler based on its JSON-RPC id.
     * If the id matches a pending request, the corresponding future is completed.
     * Otherwise, the event is queued as a server-initiated message.
     */
    private void dispatchSseEvent(String data) {
        try {
            JsonObject json = Json.createReader(new StringReader(data)).readObject();
            boolean isResponse = json.containsKey("result") || json.containsKey("error");
            if (isResponse && json.containsKey("id")) {
                long id = json.getJsonNumber("id").longValue();
                CompletableFuture<String> future = pendingResponses.remove(id);
                if (future != null) {
                    future.complete(data);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse SSE event for id correlation, treating as server-initiated: " + data);
        }
        serverInitiatedMessages.offer(data);
    }

    private String sendAndReceive(String method, JsonObject params) throws Exception {
        long id = nextId.getAndIncrement();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingResponses.put(id, future);

        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", method);
        if (params != null) {
            builder.add("params", params);
        }

        postToStreamable(builder.build().toString());
        return future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Sends a JSON-RPC message to the streamable endpoint using an existing session.
     * The response will arrive on the SSE stream and be dispatched by id.
     */
    private int postToStreamable(String jsonBody) throws Exception {
        return postToStreamableWithProtocolVersion(jsonBody, null);
    }

    private int postToStreamableWithProtocolVersion(String jsonBody, String protocolVersion) throws Exception {
        URL streamUrl = new URL(deploymentUrl, "stream");
        HttpURLConnection conn = (HttpURLConnection) streamUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setRequestProperty("mcp-session-id", sessionId);
        if (protocolVersion != null) {
            conn.setRequestProperty("mcp-protocol-version", protocolVersion);
        }
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
