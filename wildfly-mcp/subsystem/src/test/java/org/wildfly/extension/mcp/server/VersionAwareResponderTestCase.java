/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ProtocolVersion;

public class VersionAwareResponderTestCase {

    @Test
    public void testModernToolsCallAddsResultTypeComplete() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = toolCallResponse("1", false);
        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernToolsCallStructuredAddsResultTypeComplete() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = toolCallResponse("2", true);
        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testLegacyToolsCallNoResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2025_11_25, 3_600_000L, "public");

        JsonObject message = toolCallResponse("3", false);
        responder.send(message);

        JsonObject result = inner.lastResult();
        assertFalse("Legacy should not have resultType", result.containsKey("resultType"));
    }

    @Test
    public void testModernResourcesReadIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObjectBuilder resultBuilder = Json.createObjectBuilder()
                .add("contents", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("uri", "test://info")
                                .add("text", "hello")));
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "4")
                .add("result", resultBuilder)
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern resources/read should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernToolsListIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "5")
                .add("result", Json.createObjectBuilder()
                        .add("tools", Json.createArrayBuilder()))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern tools/list should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testExistingResultTypeNotOverwritten() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "6")
                .add("result", Json.createObjectBuilder()
                        .add("resultType", "input_required")
                        .add("content", Json.createArrayBuilder()))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertEquals("Should preserve existing resultType", "input_required", result.getString("resultType"));
    }

    @Test
    public void testErrorResponseNotModified() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "7")
                .add("error", Json.createObjectBuilder()
                        .add("code", -32602)
                        .add("message", "Invalid params"))
                .build();

        responder.send(message);

        JsonObject sent = inner.lastMessage();
        assertFalse("Error response should not have result", sent.containsKey("result"));
    }

    @Test
    public void testModernPromptsListIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "8")
                .add("result", Json.createObjectBuilder()
                        .add("prompts", Json.createArrayBuilder()))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern prompts/list should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernPromptsGetIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "9")
                .add("result", Json.createObjectBuilder()
                        .add("messages", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder()
                                        .add("role", "assistant")
                                        .add("content", Json.createObjectBuilder()
                                                .add("type", "text")
                                                .add("text", "hello")))))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern prompts/get should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernResourcesListIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "10")
                .add("result", Json.createObjectBuilder()
                        .add("resources", Json.createArrayBuilder()))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern resources/list should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernResourceTemplatesListIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "11")
                .add("result", Json.createObjectBuilder()
                        .add("resourceTemplates", Json.createArrayBuilder()))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern resources/templates/list should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernCompletionCompleteIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "12")
                .add("result", Json.createObjectBuilder()
                        .add("completion", Json.createObjectBuilder()
                                .add("values", Json.createArrayBuilder())
                                .add("hasMore", false)
                                .add("total", 0)))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern completion/complete should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testModernPingIncludesResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "13")
                .add("result", Json.createObjectBuilder())
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertTrue("Modern ping should have resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    @Test
    public void testLegacyResourcesListNoResultType() {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2025_11_25, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "14")
                .add("result", Json.createObjectBuilder()
                        .add("resources", Json.createArrayBuilder()))
                .build();

        responder.send(message);

        JsonObject result = inner.lastResult();
        assertFalse("Legacy resources/list should not have resultType", result.containsKey("resultType"));
    }

    @Test
    public void testModernSendSyncIncludesResultType() throws InterruptedException {
        TestResponder inner = new TestResponder();
        VersionAwareResponder responder = new VersionAwareResponder(
                inner, ProtocolVersion.V_2026_07_28, 3_600_000L, "public");

        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", "15")
                .add("result", Json.createObjectBuilder()
                        .add("tools", Json.createArrayBuilder()))
                .build();

        responder.sendSync(message);

        JsonObject result = inner.lastResult();
        assertTrue("sendSync should also add resultType", result.containsKey("resultType"));
        assertEquals("complete", result.getString("resultType"));
    }

    private static JsonObject toolCallResponse(String id, boolean withStructuredContent) {
        JsonObjectBuilder resultBuilder = Json.createObjectBuilder()
                .add("content", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("type", "text")
                                .add("text", "hello")));
        if (withStructuredContent) {
            resultBuilder.add("structuredContent", Json.createObjectBuilder()
                    .add("message", "hello"));
        }
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("result", resultBuilder)
                .build();
    }
}
