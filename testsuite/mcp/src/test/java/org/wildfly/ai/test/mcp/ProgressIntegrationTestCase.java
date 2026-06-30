/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
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
 * Integration tests for MCP progress notifications, including CDI-injected Progress.
 */
public class ProgressIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    private static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    private static final String PROGRESS_TOKEN = "progressToken";

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-progress.war")
                .addClass(TestMCPProgressTool.class)
                .addClass(TestMCPInjectedProgress.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // ==================== Method Parameter Progress (tools only) ====================

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

    // ==================== CDI-Injected Progress in Tools ====================

    @Test
    public void testInjectedProgressToolNotificationsSent() throws Exception {
        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"progress-injected-test","arguments":{"steps":"3"},"_meta":{"progressToken":"injected-token-1"}}}"""
                .formatted(toolCallId);
        postToStreamable(toolCallMessage);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        for (int i = 0; i < 3; i++) {
            String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(notification).as("Should receive progress notification " + (i + 1)).isNotNull();

            JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
            assertThat(notificationJson.getString("method")).as("Should be notifications/progress")
                    .isEqualTo(NOTIFICATIONS_PROGRESS);
            JsonObject params = notificationJson.getJsonObject("params");
            assertThat(params.getString(PROGRESS_TOKEN)).as("Token should match").isEqualTo("injected-token-1");
            assertThat(params.getJsonNumber("total").intValue()).as("Total should be 3").isEqualTo(3);
            assertThat(params.getJsonNumber("progress").intValue()).as("Progress should be " + (i + 1))
                    .isEqualTo(i + 1);
        }

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should confirm completion").contains("Completed 3 steps");
    }

    @Test
    public void testInjectedProgressToolNoToken() throws Exception {
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "progress-injected-no-token")
                .add("arguments", Json.createObjectBuilder())
                .build());

        JsonObject resultJson = Json.createReader(new StringReader(response)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Should report no-token").isEqualTo("no-token");
    }

    // ==================== CDI-Injected Progress in Prompts ====================

    @Test
    public void testInjectedProgressPromptNotificationsSent() throws Exception {
        long promptCallId = nextId.getAndIncrement();
        CompletableFuture<String> promptResultFuture = new CompletableFuture<>();
        pendingResponses.put(promptCallId, promptResultFuture);

        String promptCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"prompts/get","params":{"name":"progress-prompt","arguments":{"name":"WildFly"},"_meta":{"progressToken":"prompt-token"}}}"""
                .formatted(promptCallId);
        postToStreamable(promptCallMessage);

        String promptResult = promptResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(promptResult).as("Should receive prompt result").isNotNull();

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("Should receive progress notification from prompt").isNotNull();

        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/progress")
                .isEqualTo(NOTIFICATIONS_PROGRESS);
        JsonObject params = notificationJson.getJsonObject("params");
        assertThat(params.getString(PROGRESS_TOKEN)).as("Token should match").isEqualTo("prompt-token");
        assertThat(params.getJsonNumber("progress").intValue()).as("Progress should be 1").isEqualTo(1);
        assertThat(params.getJsonNumber("total").intValue()).as("Total should be 1").isEqualTo(1);
        assertThat(params.getString("message")).as("Message should be present").isEqualTo("Generating greeting");

        JsonObject resultJson = Json.createReader(new StringReader(promptResult)).readObject();
        JsonArray messages = resultJson.getJsonObject("result").getJsonArray("messages");
        String text = messages.getJsonObject(0).getJsonObject("content").getString("text");
        assertThat(text).as("Should contain greeting").contains("Hello, WildFly!");
    }

    // ==================== CDI-Injected Progress in Resource Templates ====================

    @Test
    public void testInjectedProgressResourceTemplateNotificationsSent() throws Exception {
        long readCallId = nextId.getAndIncrement();
        CompletableFuture<String> readResultFuture = new CompletableFuture<>();
        pendingResponses.put(readCallId, readResultFuture);

        String readCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"resources/read","params":{"uri":"test://progress/42","_meta":{"progressToken":"resource-token"}}}"""
                .formatted(readCallId);
        postToStreamable(readCallMessage);

        String readResult = readResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(readResult).as("Should receive resource result").isNotNull();

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("Should receive progress notification from resource template").isNotNull();

        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/progress")
                .isEqualTo(NOTIFICATIONS_PROGRESS);
        JsonObject params = notificationJson.getJsonObject("params");
        assertThat(params.getString(PROGRESS_TOKEN)).as("Token should match").isEqualTo("resource-token");
        assertThat(params.getJsonNumber("progress").intValue()).as("Progress should be 1").isEqualTo(1);
        assertThat(params.getJsonNumber("total").intValue()).as("Total should be 1").isEqualTo(1);
        assertThat(params.getString("message")).as("Message should be present").isEqualTo("Loading resource");

        JsonObject resultJson = Json.createReader(new StringReader(readResult)).readObject();
        JsonArray contents = resultJson.getJsonObject("result").getJsonArray("contents");
        String text = contents.getJsonObject(0).getString("text");
        assertThat(text).as("Should contain resource data").isEqualTo("data-for-42");
    }
}
