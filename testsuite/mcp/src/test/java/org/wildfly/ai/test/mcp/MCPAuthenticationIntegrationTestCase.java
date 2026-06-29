/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.wildfly.ai.test.container.KeycloakContainerManager;

/**
 * Integration tests for MCP server OIDC authentication via Keycloak.
 *
 * <p>Deploys a WAR with OIDC auth-method to a WildFly server configured with
 * the {@code elytron-oidc-client} subsystem, then verifies that unauthenticated
 * requests are rejected and Bearer-token-authenticated requests succeed.</p>
 */
public class MCPAuthenticationIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    static {
        KeycloakContainerManager.ensureStarted();
    }

    private static final String INIT_MESSAGE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","clientInfo":{"name":"test-client","version":"1.0.0"},"capabilities":{}}}""";

    private static final String WEB_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
               version="6.0">
                <session-config>
                    <session-timeout>30</session-timeout>
                </session-config>
                <login-config>
                    <auth-method>OIDC</auth-method>
                </login-config>
            </web-app>
            """;

    private String accessToken;

    @Deployment(testable = false)
    @TargetsContainer("wildfly-managed-oidc")
    public static WebArchive createDeployment() {
        String keycloakBaseUrl = KeycloakContainerManager.getKeycloakUrl();
        if (keycloakBaseUrl == null) {
            keycloakBaseUrl = "http://localhost:8080";
        }
        String oidcJson = """
                {
                    "client-id": "mcp-client",
                    "provider-url": "%s/realms/mcp-test-realm",
                    "ssl-required": "NONE",
                    "public-client": true,
                    "principal-attribute": "preferred_username"
                }
                """.formatted(keycloakBaseUrl);

        return ShrinkWrap.create(WebArchive.class, "mcp-auth-test.war")
                .addClass(TestMCPTool.class)
                .addClass(TestMCPTool.AddResult.class)
                .addAsWebInfResource(new StringAsset("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee "
                        + "https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd\" "
                        + "bean-discovery-mode=\"all\"/>"), "beans.xml")
                .addAsWebInfResource(new StringAsset(WEB_XML), "web.xml")
                .addAsWebInfResource(new StringAsset(oidcJson), "oidc.json");
    }

    @BeforeAll
    public void obtainToken() throws Exception {
        assumeTrue(KeycloakContainerManager.isAvailable(), "Keycloak is not available (Docker required)");
        accessToken = KeycloakContainerManager.obtainAccessToken();
        assertThat(accessToken).as("Should obtain a valid access token").isNotNull().isNotEmpty();
    }

    @Override
    protected void configureRequestHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
    }

    // ==================== Authentication Tests ====================

    @Test
    public void testUnauthenticatedRequestIsRejected() throws Exception {
        int statusCode = sendRawRequest(null);
        // OIDC may return 401 directly or 302 redirect to the IdP login page
        assertThat(statusCode)
                .as("Unauthenticated request should be rejected (401 or 302 redirect)")
                .isIn(401, 302);
    }

    @Test
    public void testInvalidTokenIsRejected() throws Exception {
        int statusCode = sendRawRequest("Bearer invalid.token.value");
        // OIDC may return 401 directly or 302 redirect to the IdP login page
        assertThat(statusCode)
                .as("Invalid token should be rejected (401 or 302 redirect)")
                .isIn(401, 302);
    }

    private int sendRawRequest(String authorizationHeader) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) deploymentUrl.toURI().resolve("stream").toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json, text/event-stream");
            if (authorizationHeader != null) {
                conn.setRequestProperty("Authorization", authorizationHeader);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(false);

            try (var os = conn.getOutputStream()) {
                os.write(INIT_MESSAGE.getBytes(StandardCharsets.UTF_8));
            }

            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    @Test
    public void testAuthenticatedToolsListSucceeds() throws Exception {
        String response = sendAndReceive("tools/list", null);

        assertThat(response).as("Should contain tools array").contains("\"tools\"");
        assertThat(response).as("Should list the add tool").contains("\"add\"");
        assertThat(response).as("Should list the echo tool").contains("\"echo\"");
    }

    @Test
    public void testAuthenticatedToolCallSucceeds() throws Exception {
        String response = sendAndReceive("tools/call", Json.createObjectBuilder()
                .add("name", "echo")
                .add("arguments", Json.createObjectBuilder().add("message", "authenticated MCP"))
                .build());

        JsonObject jsonResponse = Json.createReader(new StringReader(response)).readObject();
        JsonObject result = jsonResponse.getJsonObject("result");
        assertThat(result).as("Should contain result").isNotNull();
        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .as("Should contain echoed message")
                .contains("authenticated MCP");
    }
}
