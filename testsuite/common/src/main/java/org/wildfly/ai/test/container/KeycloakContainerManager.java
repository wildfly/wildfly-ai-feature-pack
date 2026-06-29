/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.container;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import dasniko.testcontainers.keycloak.KeycloakContainer;

/**
 * Singleton manager for the Keycloak container lifecycle.
 *
 * <p>Starts a Keycloak container with a pre-configured realm for OIDC
 * authentication testing. The realm includes a public client ({@code mcp-client})
 * and a test user ({@code testuser}/{@code testpassword}).</p>
 *
 * <p>System properties set by this manager:</p>
 * <ul>
 *   <li>{@code keycloak.url} - Keycloak auth server URL (e.g. {@code http://localhost:32768})</li>
 * </ul>
 */
public class KeycloakContainerManager {

    private static final Logger LOG = Logger.getLogger(KeycloakContainerManager.class.getName());
    private static final String KEYCLOAK_IMAGE = System.getProperty("keycloack.image", "quay.io/keycloak/keycloak:26.6.3");
    private static final String REALM_NAME = "mcp-test-realm";
    private static final String CLIENT_ID = "mcp-client";
    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testpassword";

    private static volatile KeycloakContainer keycloak;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile String keycloakUrl;

    static {
        try {
            initializeContainer();
            available = true;
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Keycloak initialization skipped: " + e.getMessage(), e);
            LOG.warning("Tests requiring Keycloak will be disabled");
            available = false;
        }
    }

    @SuppressWarnings("resource")
    static void initializeContainer() {
        if (initialized) {
            return;
        }
        initialized = true;

        LOG.info("Starting Keycloak container with image " + KEYCLOAK_IMAGE + " (this may take a few minutes)...");
        keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
                .withRealmImportFile("/keycloak/mcp-test-realm-realm.json");
        keycloak.start();

        keycloakUrl = keycloak.getAuthServerUrl();
        System.setProperty("keycloak.url", keycloakUrl);

        ContainerLifecycleUtil.registerShutdownHook(keycloak, "Keycloak");

        LOG.info("Started Keycloak container:");
        LOG.info("  Auth URL:       " + keycloakUrl);
        LOG.info("  Realm:          " + REALM_NAME);
        LOG.info("  Client ID:      " + CLIENT_ID);
        LOG.info("  Token endpoint: " + getTokenEndpoint());
    }

    public static void ensureStarted() {
        // Triggers static initializer if not yet run
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String getKeycloakUrl() {
        return keycloakUrl;
    }

    public static String getTokenEndpoint() {
        return keycloakUrl + "/realms/" + REALM_NAME + "/protocol/openid-connect/token";
    }

    public static String obtainAccessToken() throws IOException, URISyntaxException {
        return obtainAccessToken(TEST_USER, TEST_PASSWORD);
    }

    public static String obtainAccessToken(String username, String password) throws IOException, URISyntaxException {
        HttpURLConnection conn = (HttpURLConnection) new URI(getTokenEndpoint()).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            String body = "grant_type=password"
                    + "&client_id=" + CLIENT_ID
                    + "&username=" + username
                    + "&password=" + password;

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String error = "";
                try (var err = conn.getErrorStream()) {
                    if (err != null) {
                        error = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                throw new IOException("Failed to obtain token: HTTP " + status + " " + error);
            }

            String response;
            try (var in = conn.getInputStream()) {
                response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonObject json = Json.createReader(new StringReader(response)).readObject();
            if (!json.containsKey("access_token")) {
                throw new IOException("No access_token in response: " + response);
            }
            return json.getString("access_token");
        } finally {
            conn.disconnect();
        }
    }
}
