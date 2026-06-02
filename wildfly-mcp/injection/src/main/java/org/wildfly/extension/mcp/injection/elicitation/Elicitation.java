/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;

/**
 * Describes an elicitation that a tool sends to the MCP client to collect
 * additional user input.
 *
 * <p>Two modes are supported:</p>
 * <ul>
 *   <li><b>Form mode</b> — collects structured data via an in-band form. Build with {@link #formBuilder(String)}.</li>
 *   <li><b>URL mode</b> — directs the user to an external URL for out-of-band interaction
 *       (e.g. OAuth, password entry). Build with {@link #urlBuilder(String, String)}.</li>
 * </ul>
 *
 * <p>Form mode example:</p>
 * <pre>{@code
 * Elicitation elicitation = Elicitation.formBuilder("Please provide your GitHub username")
 *     .addSchemaProperty("username", new StringSchema(true))
 *     .addSchemaProperty("notify", new BooleanSchema(false))
 *     .build();
 * }</pre>
 *
 * <p>URL mode example:</p>
 * <pre>{@code
 * Elicitation elicitation = Elicitation.urlBuilder("Please authenticate",
 *          "https://example.com/oauth")
 *     .elicitationId("auth-123")
 *     .build();
 * }</pre>
 */
public final class Elicitation {

    public enum Mode { FORM, URL }

    private final Mode mode;
    private final String message;
    private final long timeoutMillis;
    // Form-mode fields
    private final Map<String, PrimitiveSchema> schemaProperties;
    // URL-mode fields
    private final String url;
    private final String elicitationId;

    private Elicitation(Mode mode, String message, long timeoutMillis,
                        Map<String, PrimitiveSchema> schemaProperties,
                        String url, String elicitationId) {
        this.mode = mode;
        this.message = message;
        this.timeoutMillis = timeoutMillis;
        this.schemaProperties = schemaProperties;
        this.elicitationId = elicitationId;
        this.url = url;
    }

    public Mode mode() {
        return mode;
    }

    public String message() {
        return message;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public Map<String, PrimitiveSchema> schemaProperties() {
        return schemaProperties;
    }

    public String url() {
        return url;
    }

    public String elicitationId() {
        return elicitationId;
    }

    /**
     * Creates a builder for a form-mode elicitation request.
     */
    public static FormBuilder formBuilder(String message) {
        return new FormBuilder(message);
    }

    /**
     * Alias for {@link #formBuilder(String)} — preserves the original API.
     */
    public static FormBuilder builder(String message) {
        return formBuilder(message);
    }

    /**
     * Creates a builder for a URL-mode elicitation request.
     * If the {@code elicitationId} is not set on the builder, a random ID
     * is generated.
     */
    public static UrlBuilder urlBuilder(String message, String url) {
        return new UrlBuilder(message, url);
    }

    public static final class FormBuilder {

        private final String message;
        private final Map<String, PrimitiveSchema> schemaProperties = new LinkedHashMap<>();
        private long timeoutMillis = 30_000L;

        public FormBuilder(String message) {
            this.message = requireNonNull(message, "message must not be null");
        }

        public FormBuilder addSchemaProperty(String key, PrimitiveSchema schema) {
            requireNonNull(key, "key must not be null");
            requireNonNull(schema, "schema must not be null");
            schemaProperties.put(key, schema);
            return this;
        }

        public FormBuilder timeout(long millis) {
            if (millis <= 0) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeoutMillis = millis;
            return this;
        }

        public Elicitation build() {
            if (schemaProperties.isEmpty()) {
                throw new IllegalStateException("At least one schema property must be added");
            }
            return new Elicitation(Mode.FORM, message, timeoutMillis,
                    Collections.unmodifiableMap(new LinkedHashMap<>(schemaProperties)),
                    null, null);
        }
    }

    public static final class UrlBuilder {

        private final String message;
        private final String url;
        private String elicitationId;
        private long timeoutMillis = 30_000L;

        public UrlBuilder(String message, String url) {
            this.message = requireNonNull(message, "message must not be null");
            this.url = requireNonNull(url, "url must not be null");
        }

        public UrlBuilder elicitationId(String elicitationId) {
            this.elicitationId = requireNonNull(elicitationId, "elicitationId must not be null");
            return this;
        }

        public UrlBuilder timeout(long millis) {
            if (millis <= 0) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeoutMillis = millis;
            return this;
        }

        public Elicitation build() {
            String elicitationId = this.elicitationId != null ? this.elicitationId : randomUUID().toString();
            return new Elicitation(Mode.URL, message, timeoutMillis,
                    null, url, elicitationId);
        }
    }

    /**
     * The client's response to an elicitation request.
     */
    public record Response(Action action, Map<String, Object> content) {

        public enum Action {
            ACCEPT,
            DECLINE,
            CANCEL
        }

        public Response(Action action, Map<String, Object> content) {
            this.action = action;
            this.content = content != null ? Collections.unmodifiableMap(content) : Collections.emptyMap();
        }

        public boolean isAccepted() {
            return action == Action.ACCEPT;
        }

        public boolean isDeclined() {
            return action == Action.DECLINE;
        }

        public boolean isCancelled() {
            return action == Action.CANCEL;
        }

        public String getString(String key) {
            Object v = content.get(key);
            return v instanceof String s ? s : null;
        }

        public Boolean getBoolean(String key) {
            Object v = content.get(key);
            return v instanceof Boolean b ? b : null;
        }

        public Integer getInteger(String key) {
            Object v = content.get(key);
            return v instanceof Number n ? n.intValue() : null;
        }

        public Number getNumber(String key) {
            Object v = content.get(key);
            return v instanceof Number n ? n : null;
        }

        @SuppressWarnings("unchecked")
        public List<String> getStrings(String key) {
            Object v = content.get(key);
            return v instanceof List ? (List<String>) v : null;
        }
    }
}
