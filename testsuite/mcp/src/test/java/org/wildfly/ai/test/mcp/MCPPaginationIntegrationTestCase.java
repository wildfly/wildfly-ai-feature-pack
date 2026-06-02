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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for cursor-based pagination in the MCP server.
 *
 * <p>Deploys a WAR with 12 tools, 6 prompts, and 6 resources — enough to trigger
 * multi-page responses when the server's default page size is smaller than the
 * total number of registered items.  Each test navigates through pages using the
 * {@code nextCursor} field returned by list endpoints and verifies that:</p>
 * <ul>
 *   <li>All registered items are reachable across pages with no duplicates.</li>
 *   <li>The last page omits {@code nextCursor}.</li>
 *   <li>An invalid cursor is handled gracefully (error or first-page fallback).</li>
 *   <li>A cursor issued by one list type does not silently corrupt a different type's listing.</li>
 * </ul>
 *
 * <p>When the server returns all items in a single response (no pagination), the
 * page-traversal tests still pass — they simply find all items on the first (and
 * only) page.  Pagination-specific assertions (e.g. cursor cross-type isolation)
 * are skipped automatically when no {@code nextCursor} is available.</p>
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MCPPaginationIntegrationTestCase {

    private static final int MAX_PAGES = 50;

    private static String sessionId;
    private static BlockingQueue<String> sseResponses;
    private static Thread sseReaderThread;
    private static HttpURLConnection sseConnection;

    /** Captured from the first {@code tools/list} pagination traversal; used in the cross-type test. */
    private static String capturedToolsNextCursor = null;

    private static int idCounter = 1;

    @ArquillianResource
    private URL deploymentUrl;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-pagination-test.war")
                .addClass(TestMCPPaginationFixtures.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // ==================== Session Setup ====================

    @Test
    @Order(1)
    public void testInitialize() throws Exception {
        sseResponses = new LinkedBlockingQueue<>();

        String initMessage = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"pagination-test-client","version":"1.0.0"},"capabilities":{}}}""";

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
        assertThat(sessionId).as("Session ID should be returned in response header").isNotNull();

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
                // Connection closed or read error — expected during shutdown
            }
        }, "sse-pagination-reader");
        sseReaderThread.setDaemon(true);
        sseReaderThread.start();

        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Should receive initialize response").isNotNull();
        assertThat(response).as("Initialize response should contain protocolVersion").contains("protocolVersion");
        assertThat(response).as("Initialize response should advertise tools capability").contains("tools");
        assertThat(response).as("Initialize response should advertise prompts capability").contains("prompts");
        assertThat(response).as("Initialize response should advertise resources capability").contains("resources");

        idCounter = 2;
    }

    @Test
    @Order(2)
    public void testInitializedNotification() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        int statusCode = postToStreamable("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}""");
        assertThat(statusCode).as("Initialized notification should return 200").isEqualTo(200);
    }

    // ==================== Pagination: Tools ====================

    @Test
    @Order(3)
    public void testToolsListAllPagesCollected() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        Set<String> expectedTools = Set.of(
                "multiply", "divide", "subtract", "square", "cube",
                "negate", "absolute", "max-of-two", "min-of-two",
                "is-even", "power", "modulo");

        List<String> collectedNames = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        do {
            postToStreamable(buildListRequest("tools/list", ++idCounter, cursor));
            String response = sseResponses.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("Should receive tools/list response (page %d)", pageCount + 1).isNotNull();

            JsonObject json = Json.createReader(new StringReader(response)).readObject();
            assertThat(json.containsKey("result")).as("tools/list response should be a result, not an error").isTrue();

            JsonObject result = json.getJsonObject("result");
            assertThat(result.containsKey("tools")).as("Result should contain a 'tools' array").isTrue();

            JsonArray tools = result.getJsonArray("tools");
            for (JsonObject tool : tools.getValuesAs(JsonObject.class)) {
                collectedNames.add(tool.getString("name"));
            }

            // Capture first available nextCursor for later cross-type isolation test
            if (capturedToolsNextCursor == null && result.containsKey("nextCursor")) {
                capturedToolsNextCursor = result.getString("nextCursor");
            }

            cursor = result.containsKey("nextCursor") ? result.getString("nextCursor") : null;
            pageCount++;

            assertThat(pageCount)
                    .as("tools/list pagination should terminate within %d pages", MAX_PAGES)
                    .isLessThanOrEqualTo(MAX_PAGES);
        } while (cursor != null);

        Set<String> collectedNamesSet = new HashSet<>(collectedNames);
        assertThat(collectedNamesSet.size())
                .as("No tool should appear on more than one page")
                .isEqualTo(collectedNames.size());

        assertThat(collectedNamesSet)
                .as("All 12 registered tools should be reachable across all pages")
                .containsAll(expectedTools);
    }

    @Test
    @Order(4)
    public void testToolsListLastPageHasNoNextCursor() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        // Iterate through all pages and verify the final one has no nextCursor
        String cursor = null;
        JsonObject lastResult = null;

        do {
            postToStreamable(buildListRequest("tools/list", ++idCounter, cursor));
            String response = sseResponses.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("Should receive tools/list response").isNotNull();

            JsonObject json = Json.createReader(new StringReader(response)).readObject();
            lastResult = json.getJsonObject("result");
            cursor = lastResult.containsKey("nextCursor") ? lastResult.getString("nextCursor") : null;

        } while (cursor != null);

        assertThat(lastResult.containsKey("nextCursor"))
                .as("The last page of tools/list must not contain a 'nextCursor' field")
                .isFalse();
    }

    // ==================== Pagination: Prompts ====================

    @Test
    @Order(5)
    public void testPromptsListAllPagesCollected() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        Set<String> expectedPrompts = Set.of(
                "summarize", "translate", "classify", "analyze", "compare", "explain");

        List<String> collectedNames = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        do {
            postToStreamable(buildListRequest("prompts/list", ++idCounter, cursor));
            String response = sseResponses.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("Should receive prompts/list response (page %d)", pageCount + 1).isNotNull();

            JsonObject json = Json.createReader(new StringReader(response)).readObject();
            assertThat(json.containsKey("result")).as("prompts/list response should be a result, not an error").isTrue();

            JsonObject result = json.getJsonObject("result");
            assertThat(result.containsKey("prompts")).as("Result should contain a 'prompts' array").isTrue();

            JsonArray prompts = result.getJsonArray("prompts");
            for (JsonObject prompt : prompts.getValuesAs(JsonObject.class)) {
                collectedNames.add(prompt.getString("name"));
            }

            cursor = result.containsKey("nextCursor") ? result.getString("nextCursor") : null;
            pageCount++;

            assertThat(pageCount)
                    .as("prompts/list pagination should terminate within %d pages", MAX_PAGES)
                    .isLessThanOrEqualTo(MAX_PAGES);
        } while (cursor != null);

        Set<String> collectedNamesSet = new HashSet<>(collectedNames);
        assertThat(collectedNamesSet.size())
                .as("No prompt should appear on more than one page")
                .isEqualTo(collectedNames.size());

        assertThat(collectedNamesSet)
                .as("All 6 registered prompts should be reachable across all pages")
                .containsAll(expectedPrompts);
    }

    // ==================== Pagination: Resources ====================

    @Test
    @Order(6)
    public void testResourcesListAllPagesCollected() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        Set<String> expectedResources = Set.of(
                "test-config", "test-metrics", "test-health",
                "test-version", "test-docs", "test-logs");

        List<String> collectedNames = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        do {
            postToStreamable(buildListRequest("resources/list", ++idCounter, cursor));
            String response = sseResponses.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("Should receive resources/list response (page %d)", pageCount + 1).isNotNull();

            JsonObject json = Json.createReader(new StringReader(response)).readObject();
            assertThat(json.containsKey("result")).as("resources/list response should be a result, not an error").isTrue();

            JsonObject result = json.getJsonObject("result");
            assertThat(result.containsKey("resources")).as("Result should contain a 'resources' array").isTrue();

            JsonArray resources = result.getJsonArray("resources");
            for (JsonObject resource : resources.getValuesAs(JsonObject.class)) {
                collectedNames.add(resource.getString("name"));
            }

            cursor = result.containsKey("nextCursor") ? result.getString("nextCursor") : null;
            pageCount++;

            assertThat(pageCount)
                    .as("resources/list pagination should terminate within %d pages", MAX_PAGES)
                    .isLessThanOrEqualTo(MAX_PAGES);
        } while (cursor != null);

        Set<String> collectedNamesSet = new HashSet<>(collectedNames);
        assertThat(collectedNamesSet.size())
                .as("No resource should appear on more than one page")
                .isEqualTo(collectedNames.size());

        assertThat(collectedNamesSet)
                .as("All 6 registered resources should be reachable across all pages")
                .containsAll(expectedResources);
    }

    // ==================== Invalid Cursor Handling ====================

    @Test
    @Order(7)
    public void testToolsListInvalidCursorHandledGracefully() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        if (capturedToolsNextCursor == null) {
            // The server returned all tools in a single page — it has no cursor support,
            // so there is nothing to test here.
            return;
        }

        // An invalid/expired cursor must not crash the server. The MCP spec allows
        // the server to return an error (INVALID_PARAMS) or to fall back to the
        // first page — both are acceptable outcomes.
        postToStreamable(buildListRequest("tools/list", ++idCounter, "this-is-not-a-valid-cursor-value"));
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Server must respond to an invalid cursor without timing out").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        if (json.containsKey("error")) {
            int errorCode = json.getJsonObject("error").getInt("code");
            assertThat(errorCode)
                    .as("If server rejects an invalid cursor, error code must be INVALID_PARAMS (-32602)")
                    .isEqualTo(-32602);
        } else {
            // Server chose first-page fallback — verify the response is still a valid tools listing
            assertThat(json.containsKey("result")).as("Fallback response must be a result, not missing").isTrue();
            assertThat(json.getJsonObject("result").containsKey("tools"))
                    .as("Fallback response must contain a 'tools' array")
                    .isTrue();
        }
    }

    // ==================== Cross-Type Cursor Isolation ====================

    @Test
    @Order(8)
    public void testCursorFromToolsNotApplicableToPrompts() throws Exception {
        assertThat(sessionId).as("Session must be initialized first").isNotNull();

        if (capturedToolsNextCursor == null) {
            // The server returned all tools in a single page; there is no cursor to test.
            return;
        }

        // Use a tools/list cursor in a prompts/list request.
        // The server must not silently serve a nonsensical result — it should either
        // return an error (INVALID_PARAMS) or treat it as the start of the prompts list.
        postToStreamable(buildListRequest("prompts/list", ++idCounter, capturedToolsNextCursor));
        String response = sseResponses.poll(10, TimeUnit.SECONDS);
        assertThat(response).as("Server must respond when a tools cursor is sent to prompts/list").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        if (json.containsKey("error")) {
            int errorCode = json.getJsonObject("error").getInt("code");
            assertThat(errorCode)
                    .as("Cross-type cursor rejection must use INVALID_PARAMS (-32602)")
                    .isEqualTo(-32602);
        } else {
            // Server accepted the request — verify it returned a valid prompts array
            // (not garbage data from the tools cursor position)
            assertThat(json.containsKey("result")).as("Response must be a result").isTrue();
            JsonObject result = json.getJsonObject("result");
            assertThat(result.containsKey("prompts"))
                    .as("When a cross-type cursor falls back, result must still be a prompts listing")
                    .isTrue();
        }
    }

    // ==================== Helpers ====================

    /**
     * Builds a JSON-RPC list request, optionally including a {@code cursor} parameter.
     * Uses the Jakarta JSON API to ensure the cursor value is safely embedded in the JSON.
     */
    private String buildListRequest(String method, int id, String cursor) {
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", method);

        if (cursor != null) {
            builder.add("params", Json.createObjectBuilder().add("cursor", cursor));
        }

        return builder.build().toString();
    }

    /**
     * POSTs a JSON-RPC message to the streamable endpoint using the active session.
     * Responses arrive on the SSE stream ({@link #sseResponses}), not in the HTTP body.
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
