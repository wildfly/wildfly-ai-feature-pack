/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Integration tests for the {@code ResourceNotifier} feature.
 *
 * <p>Covers both method-parameter injection ({@link TestMCPResourceNotifier#notifierTest})
 * and CDI injection ({@link TestMCPResourceNotifier#notifierInjectedTest}).</p>
 */
public class ResourceNotifierIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    private static final String NOTIFICATIONS_RESOURCES_UPDATED = "notifications/resources/updated";

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-resource-notifier.war")
                .addClass(TestMCPResourceNotifier.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    static Stream<String> notifierToolNames() {
        return Stream.of("notifier-test", "notifier-injected-test");
    }

    // ==================== Schema ====================

    @ParameterizedTest
    @MethodSource("notifierToolNames")
    public void testResourceNotifierNotExposedInSchema(String toolName) throws Exception {
        String response = sendAndReceive("tools/list", null);
        assertThat(response).as("Should list the %s tool", toolName).contains(toolName);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        JsonArray tools = json.getJsonObject("result").getJsonArray("tools");
        JsonObject tool = null;
        for (int i = 0; i < tools.size(); i++) {
            if (toolName.equals(tools.getJsonObject(i).getString("name"))) {
                tool = tools.getJsonObject(i);
                break;
            }
        }
        assertThat(tool).as("%s tool should be present", toolName).isNotNull();
        JsonObject inputSchema = tool.getJsonObject("inputSchema");
        JsonObject properties = inputSchema.containsKey("properties") ? inputSchema.getJsonObject("properties") : Json.createObjectBuilder().build();
        assertThat(properties.containsKey("notifier")).as("%s should not expose notifier in schema", toolName).isFalse();
        assertThat(properties.containsKey("injectedNotifier")).as("%s should not expose injectedNotifier in schema", toolName).isFalse();
    }

    // ==================== Notification sent to subscriber ====================

    @ParameterizedTest
    @MethodSource("notifierToolNames")
    public void testResourceNotifierNotificationSent(String toolName) throws Exception {
        String subscribeResponse = sendAndReceive("resources/subscribe", Json.createObjectBuilder()
                .add("uri", TestMCPResourceNotifier.NOTIFIER_RESOURCE_URI)
                .build());
        assertThat(subscribeResponse).as("Subscribe should succeed").doesNotContain("\"error\"");

        long toolCallId = nextId.getAndIncrement();
        CompletableFuture<String> toolResultFuture = new CompletableFuture<>();
        pendingResponses.put(toolCallId, toolResultFuture);

        String toolCallMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"%s","arguments":{}}}"""
                .formatted(toolCallId, toolName);
        postToStreamable(toolCallMessage);

        String toolResult = toolResultFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(toolResult).as("Should receive tool result").isNotNull();

        JsonObject resultJson = Json.createReader(new StringReader(toolResult)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Tool should return 'Notified'").isEqualTo("Notified");

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("Should receive a resource-updated notification").isNotNull();

        JsonObject notificationJson = Json.createReader(new StringReader(notification)).readObject();
        assertThat(notificationJson.getString("method")).as("Should be notifications/resources/updated")
                .isEqualTo(NOTIFICATIONS_RESOURCES_UPDATED);
        assertThat(notificationJson.getJsonObject("params").getString("uri"))
                .as("URI should match the notified resource")
                .isEqualTo(TestMCPResourceNotifier.NOTIFIER_RESOURCE_URI);

        sendAndReceive("resources/unsubscribe", Json.createObjectBuilder()
                .add("uri", TestMCPResourceNotifier.NOTIFIER_RESOURCE_URI)
                .build());
    }

    // ==================== No notification without subscriber ====================

    @Test
    public void testResourceNotifierNoSubscriberNoNotification() throws Exception {
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "notifier-test")
                .add("arguments", Json.createObjectBuilder())
                .build());

        JsonObject resultJson = Json.createReader(new StringReader(response)).readObject();
        JsonArray content = resultJson.getJsonObject("result").getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text")).as("Tool should return 'Notified'").isEqualTo("Notified");

        String notification = serverInitiatedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(notification).as("No notification should be sent when there are no subscribers").isNull();
    }
}
