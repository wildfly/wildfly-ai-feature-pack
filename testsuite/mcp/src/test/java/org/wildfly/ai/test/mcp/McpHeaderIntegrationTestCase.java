/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code @McpHeader} annotation.
 *
 * <p>Deploys a WAR with {@link TestMCPHeaderTool} and verifies that:
 * <ul>
 *   <li>Header params are included in the tool's input schema with {@code x-mcp-header}</li>
 *   <li>Header values are resolved from {@code x-mcp-header-*} HTTP headers</li>
 *   <li>Header values fall back to {@code params._meta.headers}</li>
 *   <li>Missing required headers return a proper error</li>
 * </ul>
 */
public class McpHeaderIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    private final Map<String, String> extraHeaders = new ConcurrentHashMap<>();

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-header-test.war")
                .addClass(TestMCPHeaderTool.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Override
    protected void configureRequestHeaders(HttpURLConnection conn) {
        for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void testHeaderParamsIncludedInInputSchemaWithAnnotation() throws Exception {
        String response = sendAndReceive("tools/list", null);
        JsonObject json = parseResponse(response);
        JsonArray tools = json.getJsonObject("result").getJsonArray("tools");

        JsonObject greetTool = findTool(tools, "greet-with-header");
        assertThat(greetTool).as("greet-with-header tool should be listed").isNotNull();

        JsonObject inputSchema = greetTool.getJsonObject("inputSchema");
        assertThat(inputSchema).as("inputSchema should be present").isNotNull();

        JsonObject properties = inputSchema.getJsonObject("properties");
        assertThat(properties.containsKey("name"))
                .as("Regular arg 'name' should be in inputSchema")
                .isTrue();
        assertThat(properties.containsKey("language"))
                .as("Header param should appear under its headerName 'language'")
                .isTrue();
        assertThat(properties.getJsonObject("language").getString("x-mcp-header"))
                .as("x-mcp-header annotation should be present")
                .isEqualTo("language");
    }

    @Test
    public void testHeaderOnlyToolHasHeaderProperty() throws Exception {
        String response = sendAndReceive("tools/list", null);
        JsonObject json = parseResponse(response);
        JsonArray tools = json.getJsonObject("result").getJsonArray("tools");

        JsonObject echoTool = findTool(tools, "header-only-echo");
        assertThat(echoTool).as("header-only-echo tool should be listed").isNotNull();

        JsonObject properties = echoTool.getJsonObject("inputSchema").getJsonObject("properties");
        assertThat(properties.size())
                .as("Header-only tool should have one property for the header param")
                .isEqualTo(1);
        assertThat(properties.containsKey("echo-value"))
                .as("Header param should appear under its headerName 'echo-value'")
                .isTrue();
        assertThat(properties.getJsonObject("echo-value").getString("x-mcp-header"))
                .as("x-mcp-header annotation should be present")
                .isEqualTo("echo-value");
    }

    @Test
    public void testToolCallWithHttpHeader() throws Exception {
        try {
            extraHeaders.put("x-mcp-header-language", "fr");
            String response = sendAndReceive("tools/call",
                    Json.createObjectBuilder()
                            .add("name", "greet-with-header")
                            .add("arguments", Json.createObjectBuilder().add("name", "WildFly"))
                            .build());

            JsonObject json = parseResponse(response);
            JsonObject result = json.getJsonObject("result");
            assertThat(result).as("Should contain result").isNotNull();

            JsonArray content = result.getJsonArray("content");
            assertThat(content.getJsonObject(0).getString("text"))
                    .as("Should greet in French via HTTP header")
                    .contains("Bonjour, WildFly");
        } finally {
            extraHeaders.clear();
        }
    }

    @Test
    public void testToolCallWithMetaHeaders() throws Exception {
        String response = sendAndReceive("tools/call",
                Json.createObjectBuilder()
                        .add("name", "greet-with-header")
                        .add("arguments", Json.createObjectBuilder().add("name", "WildFly"))
                        .add("_meta", Json.createObjectBuilder()
                                .add("headers", Json.createObjectBuilder()
                                        .add("language", "es")))
                        .build());

        JsonObject json = parseResponse(response);
        JsonObject result = json.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();

        JsonArray content = result.getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text"))
                .as("Should greet in Spanish via _meta.headers fallback")
                .contains("Hola, WildFly");
    }

    @Test
    public void testHttpHeaderTakesPrecedenceOverMeta() throws Exception {
        try {
            extraHeaders.put("x-mcp-header-language", "fr");
            String response = sendAndReceive("tools/call",
                    Json.createObjectBuilder()
                            .add("name", "greet-with-header")
                            .add("arguments", Json.createObjectBuilder().add("name", "WildFly"))
                            .add("_meta", Json.createObjectBuilder()
                                    .add("headers", Json.createObjectBuilder()
                                            .add("language", "es")))
                            .build());

            JsonObject json = parseResponse(response);
            JsonObject result = json.getJsonObject("result");
            assertThat(result).as("Should contain result").isNotNull();

            JsonArray content = result.getJsonArray("content");
            assertThat(content.getJsonObject(0).getString("text"))
                    .as("HTTP header should take precedence over _meta.headers")
                    .contains("Bonjour, WildFly");
        } finally {
            extraHeaders.clear();
        }
    }

    @Test
    public void testOptionalHeaderMissingDefaultsToNull() throws Exception {
        String response = sendAndReceive("tools/call",
                Json.createObjectBuilder()
                        .add("name", "greet-with-header")
                        .add("arguments", Json.createObjectBuilder().add("name", "WildFly"))
                        .build());

        JsonObject json = parseResponse(response);
        JsonObject result = json.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();

        JsonArray content = result.getJsonArray("content");
        assertThat(content.getJsonObject(0).getString("text"))
                .as("Missing optional header should default (English)")
                .contains("Hello, WildFly");
    }

    @Test
    public void testRequiredHeaderPresent() throws Exception {
        try {
            extraHeaders.put("x-mcp-header-echo-value", "test-data");
            String response = sendAndReceive("tools/call",
                    Json.createObjectBuilder()
                            .add("name", "header-only-echo")
                            .build());

            JsonObject json = parseResponse(response);
            JsonObject result = json.getJsonObject("result");
            assertThat(result).as("Should contain result").isNotNull();

            JsonArray content = result.getJsonArray("content");
            assertThat(content.getJsonObject(0).getString("text"))
                    .as("Required header value should be echoed")
                    .isEqualTo("echo: test-data");
        } finally {
            extraHeaders.clear();
        }
    }

    @Test
    public void testRequiredHeaderMissing() throws Exception {
        String response = sendAndReceive("tools/call",
                Json.createObjectBuilder()
                        .add("name", "header-only-echo")
                        .build());

        JsonObject json = parseResponse(response);
        JsonObject error = json.getJsonObject("error");
        assertThat(error).as("Missing required header should return a JSON-RPC error").isNotNull();
        assertThat(error.getInt("code")).as("Error code should be -32602 (Invalid Params)").isEqualTo(-32602);
        assertThat(error.getString("message"))
                .as("Error message should mention the missing header")
                .containsIgnoringCase("header");
    }

    // ==================== Helpers ====================

    private JsonObject findTool(JsonArray tools, String name) {
        for (int i = 0; i < tools.size(); i++) {
            JsonObject tool = tools.getJsonObject(i);
            if (name.equals(tool.getString("name"))) {
                return tool;
            }
        }
        return null;
    }
}
