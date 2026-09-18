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
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code server/discover} endpoint (MCP 2026-07-28).
 *
 * <p>Validates that {@code server/discover} returns the correct response structure
 * both as the first message on a fresh connection (before initialization) and on
 * an already-initialized session.</p>
 */
public class MCPServerDiscoverIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        return createStandardMCPDeployment("mcp-discover.war");
    }

    @Test
    void testDiscoverOnInitializedSession() throws Exception {
        String response = sendAndReceive("server/discover", null);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        JsonObject result = json.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();

        JsonArray versions = result.getJsonArray("supportedVersions");
        assertThat(versions).as("Should contain supportedVersions").isNotNull();
        assertThat(versions.size()).as("Should support two protocol versions").isGreaterThanOrEqualTo(2);
        List<String> versionStrings = versions.getValuesAs(JsonString::getString);
        assertThat(versionStrings).as("Should include 2025-11-25").contains("2025-11-25");
        assertThat(versionStrings).as("Should include 2026-07-28").contains("2026-07-28");

        assertThat(result.containsKey("capabilities")).as("Should contain capabilities").isTrue();
        assertThat(result.containsKey("serverInfo")).as("Should contain serverInfo").isTrue();
        assertThat(result.containsKey("extensions")).as("Should contain extensions").isTrue();
    }

    @Test
    void testDiscoverCapabilities() throws Exception {
        String response = sendAndReceive("server/discover", null);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        JsonObject capabilities = json.getJsonObject("result").getJsonObject("capabilities");
        assertThat(capabilities).as("Capabilities should not be null").isNotNull();

        assertThat(capabilities.containsKey("tools")).as("Should advertise tools capability").isTrue();
        assertThat(capabilities.containsKey("prompts")).as("Should advertise prompts capability").isTrue();
        assertThat(capabilities.containsKey("resources")).as("Should advertise resources capability").isTrue();
        assertThat(capabilities.containsKey("completions")).as("Should advertise completions capability").isTrue();

        assertThat(capabilities.getJsonObject("tools").getBoolean("listChanged"))
                .as("Tools should support listChanged").isTrue();
        assertThat(capabilities.getJsonObject("prompts").getBoolean("listChanged"))
                .as("Prompts should support listChanged").isTrue();
        assertThat(capabilities.getJsonObject("resources").getBoolean("listChanged"))
                .as("Resources should support listChanged").isTrue();
        assertThat(capabilities.getJsonObject("resources").getBoolean("subscribe"))
                .as("Resources should support subscribe").isTrue();
    }

    @Test
    void testDiscoverServerInfo() throws Exception {
        String response = sendAndReceive("server/discover", null);

        JsonObject json = Json.createReader(new StringReader(response)).readObject();
        JsonObject serverInfo = json.getJsonObject("result").getJsonObject("serverInfo");
        assertThat(serverInfo).as("serverInfo should not be null").isNotNull();
        assertThat(serverInfo.containsKey("name")).as("serverInfo should contain name").isTrue();
        assertThat(serverInfo.containsKey("version")).as("serverInfo should contain version").isTrue();
        assertThat(serverInfo.getString("name")).as("Server name should not be empty").isNotEmpty();
        assertThat(serverInfo.getString("version")).as("Server version should not be empty").isNotEmpty();
    }

    @Test
    void testDiscoverBeforeInitialization() throws Exception {
        String data = sendDiscoverOnFreshConnection();
        assertThat(data).as("Should receive discover response").isNotNull();

        JsonObject json = Json.createReader(new StringReader(data)).readObject();
        assertThat(json.containsKey("result")).as("Should be a successful result").isTrue();

        JsonObject result = json.getJsonObject("result");

        JsonArray versions = result.getJsonArray("supportedVersions");
        assertThat(versions).as("Should contain supportedVersions").isNotNull();
        List<String> versionStrings = versions.getValuesAs(JsonString::getString);
        assertThat(versionStrings).as("Should include legacy version").contains("2025-11-25");
        assertThat(versionStrings).as("Should include modern version").contains("2026-07-28");

        assertThat(result.containsKey("capabilities")).as("Should contain capabilities").isTrue();
        assertThat(result.containsKey("serverInfo")).as("Should contain serverInfo").isTrue();
        assertThat(result.containsKey("extensions")).as("Should contain extensions").isTrue();
    }

    private String sendDiscoverOnFreshConnection() throws Exception {
        String discoverMessage = """
                {"jsonrpc":"2.0","id":1,"method":"server/discover"}""";

        URL streamUrl = deploymentUrl.toURI().resolve("stream").toURL();
        HttpURLConnection conn = (HttpURLConnection) streamUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(discoverMessage.getBytes(StandardCharsets.UTF_8));
        }

        assertThat(conn.getResponseCode())
                .as("server/discover without session should return 200").isEqualTo(200);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
