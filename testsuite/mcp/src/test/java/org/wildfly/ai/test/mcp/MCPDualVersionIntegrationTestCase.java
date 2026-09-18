/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

/**
 * Integration tests validating MCP dual-version coexistence (2025-11-25 + 2026-07-28).
 *
 * <p>All tests run on a legacy initialized session. Modern client behavior is triggered
 * by including {@code _meta} with the {@code io.modelcontextprotocol/protocolVersion}
 * and {@code io.modelcontextprotocol/clientCapabilities} fields in request params.</p>
 *
 * <p>Verifies that:</p>
 * <ul>
 *   <li>Legacy methods continue to work without {@code _meta}</li>
 *   <li>Methods removed in 2026-07-28 are rejected when {@code _meta} declares that version</li>
 *   <li>Version-aware response shaping ({@code resultType}) works correctly</li>
 *   <li>New protocol features ({@code subscriptions/listen}) are functional</li>
 *   <li>The {@code MCP-Protocol-Version} header accepts both supported versions</li>
 * </ul>
 */
public class MCPDualVersionIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    private static final String MODERN_VERSION = "2026-07-28";
    private static final String META_PROTOCOL_VERSION_KEY = "io.modelcontextprotocol/protocolVersion";
    private static final String META_CLIENT_CAPABILITIES_KEY = "io.modelcontextprotocol/clientCapabilities";
    private static final String REMOVED_METHOD_ERROR_FRAGMENT = "removed in protocol version 2026-07-28";

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return createStandardMCPDeployment("mcp-dual-version.war");
    }

    // ==================== Protocol Version Header ====================

    @Test
    void testNegotiatedProtocolVersionHeaderAccepted() throws Exception {
        long id = nextId.getAndIncrement();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingResponses.put(id, future);

        String pingMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"ping"}""".formatted(id);

        int statusCode = postToStreamableWithProtocolVersion(pingMessage, "2025-03-26",
                java.util.Map.of("mcp-method", "ping"));
        assertThat(statusCode).as("Negotiated MCP-Protocol-Version should be accepted").isEqualTo(200);

        String response = future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(response).as("Ping response should arrive").isNotNull();
        assertThat(response).as("Ping should return a result").contains("\"result\"");
    }

    @Test
    void testMismatchedModernProtocolVersionHeaderRejected() throws Exception {
        long id = nextId.getAndIncrement();
        String pingMessage = """
                {"jsonrpc":"2.0","id":%d,"method":"ping"}""".formatted(id);

        int statusCode = postToStreamableWithProtocolVersion(pingMessage, MODERN_VERSION,
                java.util.Map.of("mcp-method", "ping"));
        assertThat(statusCode).as("Mismatched MCP-Protocol-Version should be rejected").isEqualTo(400);
    }

    // ==================== Removed Methods for Modern Clients ====================

    @Test
    void testModernClientPingRemoved() throws Exception {
        String response = sendAndReceive("ping", withModernMeta(null));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("error")).as("Ping should be rejected for modern client").isTrue();

        JsonObject error = json.getJsonObject("error");
        assertThat(error.getInt("code")).as("Error code should be METHOD_NOT_FOUND").isEqualTo(-32601);
        assertThat(error.getString("message")).as("Error should mention removed method")
                .contains(REMOVED_METHOD_ERROR_FRAGMENT);
    }

    @Test
    void testModernClientResourcesSubscribeRemoved() throws Exception {
        String response = sendAndReceive("resources/subscribe", withModernMeta(
                Json.createObjectBuilder().add("uri", "test://info").build()));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("error")).as("resources/subscribe should be rejected for modern client").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).isEqualTo(-32601);
        assertThat(json.getJsonObject("error").getString("message")).contains(REMOVED_METHOD_ERROR_FRAGMENT);
    }

    @Test
    void testModernClientResourcesUnsubscribeRemoved() throws Exception {
        String response = sendAndReceive("resources/unsubscribe", withModernMeta(
                Json.createObjectBuilder().add("uri", "test://info").build()));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("error")).as("resources/unsubscribe should be rejected for modern client").isTrue();
        assertThat(json.getJsonObject("error").getInt("code")).isEqualTo(-32601);
        assertThat(json.getJsonObject("error").getString("message")).contains(REMOVED_METHOD_ERROR_FRAGMENT);
    }

    // ==================== Modern Client — Allowed Methods ====================

    @Test
    void testModernClientToolsListSucceeds() throws Exception {
        String response = sendAndReceive("tools/list", withModernMeta(null));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("tools/list should succeed for modern client").isTrue();
        assertThat(json.getJsonObject("result").containsKey("tools")).as("Should contain tools array").isTrue();
    }

    @Test
    void testModernClientToolsCallSucceeds() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("name", "echo")
                .add("arguments", Json.createObjectBuilder().add("message", "modern client"))
                .build();

        String response = sendAndReceive("tools/call", withModernMeta(params));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("tools/call should succeed for modern client").isTrue();

        JsonObject result = json.getJsonObject("result");
        assertThat(result.containsKey("content")).as("Should contain content").isTrue();
        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .as("Should echo the message").contains("modern client");
    }

    @Test
    void testModernClientPromptsListSucceeds() throws Exception {
        String response = sendAndReceive("prompts/list", withModernMeta(null));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("prompts/list should succeed for modern client").isTrue();
        assertThat(json.getJsonObject("result").containsKey("prompts")).as("Should contain prompts array").isTrue();
    }

    @Test
    void testModernClientResourcesListSucceeds() throws Exception {
        String response = sendAndReceive("resources/list", withModernMeta(null));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("resources/list should succeed for modern client").isTrue();
        assertThat(json.getJsonObject("result").containsKey("resources")).as("Should contain resources array").isTrue();
    }

    @Test
    void testModernClientResourcesReadSucceeds() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("uri", "test://info")
                .build();

        String response = sendAndReceive("resources/read", withModernMeta(params));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("resources/read should succeed for modern client").isTrue();
        assertThat(response).as("Should contain resource content").contains("WildFly MCP Test Resource");
    }

    @Test
    void testModernClientServerDiscoverSucceeds() throws Exception {
        String response = sendAndReceive("server/discover", withModernMeta(null));

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("server/discover should succeed for modern client").isTrue();
        JsonObject result = json.getJsonObject("result");
        assertThat(result.containsKey("supportedVersions"))
                .as("Should contain supportedVersions").isTrue();
        List<String> versionStrings = result.getJsonArray("supportedVersions").stream()
                .map(v -> ((JsonString) v).getString())
                .toList();
        assertThat(versionStrings).as("Should include legacy version").contains("2025-11-25");
        assertThat(versionStrings).as("Should include modern version").contains("2026-07-28");
    }

    // ==================== Version-Aware Response Shaping ====================

    @Test
    void testModernClientToolsCallStructuredIncludesResultType() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("name", "add-structured")
                .add("arguments", Json.createObjectBuilder().add("a", 10).add("b", 32))
                .build();

        String response = sendAndReceive("tools/call", withModernMeta(params));

        JsonObject json = parseResponse(response);
        JsonObject result = json.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();
        assertThat(result.containsKey("resultType")).as("Modern client should get resultType").isTrue();
        assertThat(result.getString("resultType")).as("resultType should be complete")
                .isEqualTo("complete");
    }

    @Test
    void testModernClientToolsCallPlainIncludesResultType() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("name", "echo")
                .add("arguments", Json.createObjectBuilder().add("message", "test"))
                .build();

        String response = sendAndReceive("tools/call", withModernMeta(params));

        JsonObject json = parseResponse(response);
        JsonObject result = json.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();
        assertThat(result.containsKey("resultType")).as("Modern client should get resultType").isTrue();
        assertThat(result.getString("resultType")).as("resultType should be complete for plain tool")
                .isEqualTo("complete");
    }

    @Test
    void testLegacyClientToolsCallOmitsResultType() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("name", "add-structured")
                .add("arguments", Json.createObjectBuilder().add("a", 10).add("b", 32))
                .build();

        String response = sendAndReceive("tools/call", params);

        JsonObject json = parseResponse(response);
        JsonObject result = json.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();
        assertThat(result.containsKey("resultType")).as("Legacy client should NOT get resultType").isFalse();
    }

    // ==================== subscriptions/listen ====================

    @Test
    void testSubscriptionsListenSucceeds() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("subscriptions", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("type", "resource")
                                .add("uri", "test://info"))
                        .add(Json.createObjectBuilder()
                                .add("type", "resource")
                                .add("uri", "test://status")))
                .build();

        String response = sendAndReceive("subscriptions/listen", params);

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("subscriptions/listen should succeed").isTrue();
        assertThat(json.containsKey("error")).as("Should not contain error").isFalse();
    }

    @Test
    void testSubscriptionsListenEmptyList() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("subscriptions", Json.createArrayBuilder())
                .build();

        String response = sendAndReceive("subscriptions/listen", params);

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("subscriptions/listen with empty list should succeed").isTrue();
    }

    @Test
    void testSubscriptionsListenMissingParamsReturnsError() throws Exception {
        String response = sendAndReceive("subscriptions/listen", null);

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("error")).as("Missing params should return error").isTrue();
        assertThat(json.getJsonObject("error").getInt("code"))
                .as("Should be INVALID_PARAMS").isEqualTo(-32602);
    }

    // ==================== Legacy Regression ====================

    @Test
    void testLegacyPingStillWorks() throws Exception {
        String response = sendAndReceive("ping", null);
        assertThat(response).as("Legacy ping should still work").contains("\"result\"");
    }

    @Test
    void testLegacyResourcesSubscribeStillWorks() throws Exception {
        String response = sendAndReceive("resources/subscribe", Json.createObjectBuilder()
                .add("uri", "test://info")
                .build());

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("Legacy resources/subscribe should still work").isTrue();
    }

    @Test
    void testLegacyResourcesUnsubscribeStillWorks() throws Exception {
        String response = sendAndReceive("resources/unsubscribe", Json.createObjectBuilder()
                .add("uri", "test://info")
                .build());

        JsonObject json = parseResponse(response);
        assertThat(json.containsKey("result")).as("Legacy resources/unsubscribe should still work").isTrue();
    }

    // ==================== Edge Cases ====================

    @Test
    void testNotificationWithoutIdIsAccepted() throws Exception {
        String notificationBody = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""";

        int statusCode = postToStreamable(notificationBody);
        assertThat(statusCode).as("Notification without id should return 202 Accepted").isEqualTo(202);
    }

    // ==================== Helpers ====================

    private static JsonObject withModernMeta(JsonObject params) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (params != null) {
            params.forEach(builder::add);
        }
        builder.add("_meta", Json.createObjectBuilder()
                .add(META_PROTOCOL_VERSION_KEY, MODERN_VERSION)
                .add(META_CLIENT_CAPABILITIES_KEY, Json.createObjectBuilder()));
        return builder.build();
    }

}
