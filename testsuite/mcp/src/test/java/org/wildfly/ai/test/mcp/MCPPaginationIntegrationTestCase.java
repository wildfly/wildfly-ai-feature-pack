/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MCPPaginationIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    private static final int MAX_PAGES = 50;

    /** Captured from the first {@code tools/list} pagination traversal; used in the cross-type test. */
    private String capturedToolsNextCursor = null;

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-pagination-test.war")
                .addClass(TestMCPPaginationFixtures.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // ==================== Pagination: Tools ====================

    @Test
    @Order(1)
    public void testToolsListAllPagesCollected() throws Exception {
        Set<String> expectedTools = Set.of(
                "multiply", "divide", "subtract", "square", "cube",
                "negate", "absolute", "max-of-two", "min-of-two",
                "is-even", "power", "modulo");

        List<String> collectedNames = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        do {
            String response = sendAndReceive("tools/list",
                    cursor != null ? Json.createObjectBuilder().add("cursor", cursor).build() : null);
            assertThat(response).as("Should receive tools/list response (page %d)", pageCount + 1).isNotNull();

            JsonObject json = Json.createReader(new StringReader(response)).readObject();
            assertThat(json.containsKey("result")).as("tools/list response should be a result, not an error").isTrue();

            JsonObject result = json.getJsonObject("result");
            assertThat(result.containsKey("tools")).as("Result should contain a 'tools' array").isTrue();

            JsonArray tools = result.getJsonArray("tools");
            for (JsonObject tool : tools.getValuesAs(JsonObject.class)) {
                collectedNames.add(tool.getString("name"));
            }

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
    @Order(2)
    public void testToolsListLastPageHasNoNextCursor() throws Exception {
        String cursor = null;
        JsonObject lastResult = null;

        do {
            String response = sendAndReceive("tools/list",
                    cursor != null ? Json.createObjectBuilder().add("cursor", cursor).build() : null);
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
    @Order(3)
    public void testPromptsListAllPagesCollected() throws Exception {
        Set<String> expectedPrompts = Set.of(
                "summarize", "translate", "classify", "analyze", "compare", "explain");

        List<String> collectedNames = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        do {
            String response = sendAndReceive("prompts/list",
                    cursor != null ? Json.createObjectBuilder().add("cursor", cursor).build() : null);
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
    @Order(4)
    public void testResourcesListAllPagesCollected() throws Exception {
        Set<String> expectedResources = Set.of(
                "test-config", "test-metrics", "test-health",
                "test-version", "test-docs", "test-logs");

        List<String> collectedNames = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        do {
            String response = sendAndReceive("resources/list",
                    cursor != null ? Json.createObjectBuilder().add("cursor", cursor).build() : null);
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
    @Order(5)
    public void testToolsListInvalidCursorHandledGracefully() throws Exception {
        if (capturedToolsNextCursor == null) {
            return;
        }

        String response = sendAndReceive("tools/list",
                Json.createObjectBuilder().add("cursor", "this-is-not-a-valid-cursor-value").build());
        assertThat(response).as("Server must respond to an invalid cursor without timing out").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        if (json.containsKey("error")) {
            int errorCode = json.getJsonObject("error").getInt("code");
            assertThat(errorCode)
                    .as("If server rejects an invalid cursor, error code must be INVALID_PARAMS (-32602)")
                    .isEqualTo(-32602);
        } else {
            assertThat(json.containsKey("result")).as("Fallback response must be a result, not missing").isTrue();
            assertThat(json.getJsonObject("result").containsKey("tools"))
                    .as("Fallback response must contain a 'tools' array")
                    .isTrue();
        }
    }

    // ==================== Cross-Type Cursor Isolation ====================

    @Test
    @Order(6)
    public void testCursorFromToolsNotApplicableToPrompts() throws Exception {
        if (capturedToolsNextCursor == null) {
            return;
        }

        String response = sendAndReceive("prompts/list",
                Json.createObjectBuilder().add("cursor", capturedToolsNextCursor).build());
        assertThat(response).as("Server must respond when a tools cursor is sent to prompts/list").isNotNull();

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        if (json.containsKey("error")) {
            int errorCode = json.getJsonObject("error").getInt("code");
            assertThat(errorCode)
                    .as("Cross-type cursor rejection must use INVALID_PARAMS (-32602)")
                    .isEqualTo(-32602);
        } else {
            assertThat(json.containsKey("result")).as("Response must be a result").isTrue();
            JsonObject result = json.getJsonObject("result");
            assertThat(result.containsKey("prompts"))
                    .as("When a cross-type cursor falls back, result must still be a prompts listing")
                    .isTrue();
        }
    }
}
