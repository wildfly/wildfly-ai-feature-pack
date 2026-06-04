/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests verifying that MCP list-changed notifications are delivered
 * to connected clients when a deployment is undeployed.
 *
 * <p>Uses an unmanaged Arquillian deployment so the test controls the deploy/undeploy
 * lifecycle and can observe the notifications sent by {@code broadcastThenShutdown}
 * before the connections are closed.</p>
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class MCPListChangedNotificationTestCase {

    private static final long RESPONSE_TIMEOUT_SECONDS = 10;
    private static final String DEPLOYMENT_NAME = "mcp-notif-test";

    @Deployment(name = DEPLOYMENT_NAME, managed = false, testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war")
                .addClass(TestMCPTool.class)
                .addClass(TestMCPTool.AddResult.class)
                .addClass(TestMCPPrompt.class)
                .addClass(TestMCPResource.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @ArquillianResource
    private Deployer deployer;

    @Test
    public void testListChangedNotificationsOnUndeploy() throws Exception {
        deployer.deploy(DEPLOYMENT_NAME);
        try {
            NotificationCapture capture = connectAndInitialize();
            try {
                deployer.undeploy(DEPLOYMENT_NAME);

                List<String> received = collectNotifications(capture, 3);

                assertThat(received.stream().filter(n -> n.contains("notifications/tools/list_changed")).count())
                        .as("Should receive exactly one notifications/tools/list_changed")
                        .isEqualTo(1);
                assertThat(received.stream().filter(n -> n.contains("notifications/prompts/list_changed")).count())
                        .as("Should receive exactly one notifications/prompts/list_changed")
                        .isEqualTo(1);
                assertThat(received.stream().filter(n -> n.contains("notifications/resources/list_changed")).count())
                        .as("Should receive exactly one notifications/resources/list_changed")
                        .isEqualTo(1);
            } finally {
                if (capture.sseReaderThread.isAlive()) {
                    capture.sseReaderThread.interrupt();
                }
                capture.sseConnection.disconnect();
            }
        } catch (Exception e) {
            try {
                deployer.undeploy(DEPLOYMENT_NAME);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    @Test
    public void testNotificationStructure() throws Exception {
        deployer.deploy(DEPLOYMENT_NAME);
        try {
            NotificationCapture capture = connectAndInitialize();
            try {
                deployer.undeploy(DEPLOYMENT_NAME);

                List<String> received = collectNotifications(capture, 3);

                String toolsNotification = received.stream()
                        .filter(n -> n.contains("notifications/tools/list_changed"))
                        .findFirst()
                        .orElse(null);
                assertThat(toolsNotification).as("Should receive tools/list_changed notification").isNotNull();

                JsonObject toolsJson = Json.createReader(new StringReader(toolsNotification)).readObject();
                assertThat(toolsJson.getString("jsonrpc")).as("tools notification should be JSON-RPC 2.0").isEqualTo("2.0");
                assertThat(toolsJson.getString("method")).as("Method should be notifications/tools/list_changed")
                        .isEqualTo("notifications/tools/list_changed");
                assertThat(toolsJson.containsKey("id")).as("Notification should not have an id field").isFalse();

                String promptsNotification = received.stream()
                        .filter(n -> n.contains("notifications/prompts/list_changed"))
                        .findFirst()
                        .orElse(null);
                assertThat(promptsNotification).as("Should receive prompts/list_changed notification").isNotNull();

                JsonObject json = Json.createReader(new StringReader(promptsNotification)).readObject();
                assertThat(json.getString("jsonrpc")).as("Should be JSON-RPC 2.0").isEqualTo("2.0");
                assertThat(json.getString("method")).as("Method should be notifications/prompts/list_changed")
                        .isEqualTo("notifications/prompts/list_changed");
                assertThat(json.containsKey("id")).as("Notification should not have an id field").isFalse();

                String resourcesNotification = received.stream()
                        .filter(n -> n.contains("notifications/resources/list_changed"))
                        .findFirst()
                        .orElse(null);
                assertThat(resourcesNotification).as("Should receive resources/list_changed notification").isNotNull();

                JsonObject resourcesJson = Json.createReader(new StringReader(resourcesNotification)).readObject();
                assertThat(resourcesJson.getString("jsonrpc")).as("resources notification should be JSON-RPC 2.0").isEqualTo("2.0");
                assertThat(resourcesJson.getString("method")).as("Method should be notifications/resources/list_changed")
                        .isEqualTo("notifications/resources/list_changed");
                assertThat(resourcesJson.containsKey("id")).as("Notification should not have an id field").isFalse();
            } finally {
                if (capture.sseReaderThread.isAlive()) {
                    capture.sseReaderThread.interrupt();
                }
                capture.sseConnection.disconnect();
            }
        } catch (Exception e) {
            try {
                deployer.undeploy(DEPLOYMENT_NAME);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    @Test
    public void testListChangedNotificationsAfterRedeploy() throws Exception {
        deployer.deploy(DEPLOYMENT_NAME);
        try {
            // First cycle: connect and undeploy
            NotificationCapture capture1 = connectAndInitialize();
            deployer.undeploy(DEPLOYMENT_NAME);
            collectNotifications(capture1, 3);
            capture1.sseConnection.disconnect();

            // Second cycle: re-deploy, reconnect, verify tools, then undeploy again
            deployer.deploy(DEPLOYMENT_NAME);
            NotificationCapture capture2 = connectAndInitialize();
            try {
                String toolsResponse = sendAndReceive(capture2, "tools/list", null);
                assertThat(toolsResponse).as("Should list tools after redeploy").contains("\"tools\"");
                assertThat(toolsResponse).as("Should list the add tool after redeploy").contains("\"add\"");
                assertThat(toolsResponse).as("Should list the echo tool after redeploy").contains("\"echo\"");

                String promptsResponse = sendAndReceive(capture2, "prompts/list", null);
                assertThat(promptsResponse).as("Should list prompts after redeploy").contains("\"prompts\"");

                String resourcesResponse = sendAndReceive(capture2, "resources/list", null);
                assertThat(resourcesResponse).as("Should list resources after redeploy").contains("\"resources\"");

                deployer.undeploy(DEPLOYMENT_NAME);

                List<String> received = collectNotifications(capture2, 3);

                assertThat(received.stream().filter(n -> n.contains("notifications/tools/list_changed")).count())
                        .as("Should receive exactly one tools/list_changed after second undeploy")
                        .isEqualTo(1);
                assertThat(received.stream().filter(n -> n.contains("notifications/prompts/list_changed")).count())
                        .as("Should receive exactly one prompts/list_changed after second undeploy")
                        .isEqualTo(1);
                assertThat(received.stream().filter(n -> n.contains("notifications/resources/list_changed")).count())
                        .as("Should receive exactly one resources/list_changed after second undeploy")
                        .isEqualTo(1);
            } finally {
                if (capture2.sseReaderThread.isAlive()) {
                    capture2.sseReaderThread.interrupt();
                }
                capture2.sseConnection.disconnect();
            }
        } catch (Exception e) {
            try {
                deployer.undeploy(DEPLOYMENT_NAME);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private NotificationCapture connectAndInitialize() throws Exception {
        AtomicLong nextId = new AtomicLong(1);
        ConcurrentHashMap<Long, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();
        BlockingQueue<String> serverInitiatedMessages = new LinkedBlockingQueue<>();

        long initId = nextId.getAndIncrement();
        CompletableFuture<String> initFuture = new CompletableFuture<>();
        pendingResponses.put(initId, initFuture);

        String initMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"notif-test-client","version":"1.0.0"},"capabilities":{}}}"""
                .formatted(initId);

        URL streamUrl = new URL("http://127.0.0.1:8080/" + DEPLOYMENT_NAME + "/stream");
        HttpURLConnection sseConnection = (HttpURLConnection) streamUrl.openConnection();
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

        String sessionId = sseConnection.getHeaderField("mcp-session-id");
        assertThat(sessionId).as("Session ID should be returned").isNotNull();

        Thread sseReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sseConnection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        try {
                            JsonObject json = Json.createReader(new StringReader(data)).readObject();
                            boolean isResponse = json.containsKey("result") || json.containsKey("error");
                            if (isResponse && json.containsKey("id")) {
                                long id = json.getJsonNumber("id").longValue();
                                CompletableFuture<String> future = pendingResponses.remove(id);
                                if (future != null) {
                                    future.complete(data);
                                    continue;
                                }
                            }
                        } catch (Exception e) {
                            // treat as server-initiated
                        }
                        serverInitiatedMessages.offer(data);
                    }
                }
            } catch (Exception e) {
                // Connection closed — expected during undeploy
            }
        }, "sse-reader-notif-test");
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        String initResponse = initFuture.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(initResponse).as("Should receive initialize response").isNotNull();

        String initializedMessage = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""";
        postToStreamable(sessionId, initializedMessage);

        return new NotificationCapture(sseConnection, sseReaderThread, serverInitiatedMessages,
                pendingResponses, nextId, sessionId);
    }

    private List<String> collectNotifications(NotificationCapture capture, int expectedCount) throws InterruptedException {
        List<String> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + RESPONSE_TIMEOUT_SECONDS * 1000;
        while (received.size() < expectedCount && System.currentTimeMillis() < deadline) {
            String msg = capture.serverInitiatedMessages.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null) {
                received.add(msg);
            }
        }
        return received;
    }

    private String sendAndReceive(NotificationCapture capture, String method, JsonObject params) throws Exception {
        long id = capture.nextId.getAndIncrement();
        CompletableFuture<String> future = new CompletableFuture<>();
        capture.pendingResponses.put(id, future);

        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", method);
        if (params != null) {
            builder.add("params", params);
        }

        postToStreamable(capture.sessionId, builder.build().toString());
        return future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void postToStreamable(String sessionId, String jsonBody) throws Exception {
        URL streamUrl = new URL("http://127.0.0.1:8080/" + DEPLOYMENT_NAME + "/stream");
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
        assertThat(conn.getResponseCode()).as("POST should succeed").isEqualTo(200);
        conn.disconnect();
    }

    private record NotificationCapture(
            HttpURLConnection sseConnection,
            Thread sseReaderThread,
            BlockingQueue<String> serverInitiatedMessages,
            ConcurrentHashMap<Long, CompletableFuture<String>> pendingResponses,
            AtomicLong nextId,
            String sessionId) {
    }
}
