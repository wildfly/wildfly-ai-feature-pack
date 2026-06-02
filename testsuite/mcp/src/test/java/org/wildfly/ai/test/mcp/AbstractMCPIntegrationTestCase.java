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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.json.Json;
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for MCP integration tests providing shared SSE connection
 * infrastructure and JSON-RPC helper methods.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractMCPIntegrationTestCase {

    protected static final long RESPONSE_TIMEOUT_SECONDS = 10;

    private volatile boolean initialized;
    private String sessionId;
    private Thread sseReaderThread;
    private HttpURLConnection sseConnection;

    protected final AtomicLong nextId = new AtomicLong(1);
    protected final ConcurrentHashMap<Long, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();
    protected final BlockingQueue<String> serverInitiatedMessages = new LinkedBlockingQueue<>();

    @ArquillianResource
    protected URL deploymentUrl;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "mcp-test.war")
                .addClass(TestMCPTool.class)
                .addClass(TestMCPPrompt.class)
                .addClass(TestMCPResource.class)
                .addClass(TestMCPElicitationTool.class)
                .addClass(TestMCPProgressTool.class)
                .addClass(TestMCPCompletion.class)
                .addClass(TestJaxrsApplication.class)
                .addClass(TestOAuthCallbackEndpoint.class)
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
                {"jsonrpc":"2.0","id":%d,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"test-client","version":"1.0.0"},"capabilities":{"elicitation":{"form":{},"url":{}}}}}"""
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

    protected String sendAndReceive(String method, JsonObject params) throws Exception {
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

    protected int postToStreamable(String jsonBody) throws Exception {
        return postToStreamableWithProtocolVersion(jsonBody, null);
    }

    protected int postToStreamableWithProtocolVersion(String jsonBody, String protocolVersion) throws Exception {
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
