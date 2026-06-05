/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.wildfly.extension.mcp.injection.MCPLogger.ROOT_LOGGER;

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
 * Elicitation.FormBuilder form = Elicitation.formBuilder("Please provide your GitHub username");
 * StringProperty username = form.addString("username");
 * form.addBoolean("notify").optional().defaultValue(false);
 * Elicitation elicitation = form.build();
 * }</pre>
 *
 * <p>URL mode example:</p>
 * <pre>{@code
 * Elicitation req = Elicitation.urlBuilder("Please authenticate",
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
    private final List<ElicitationProperty<?>> schemaProperties;
    // URL-mode fields
    private final String url;
    private final String elicitationId;

    private Elicitation(Mode mode, String message, long timeoutMillis,
                        List<ElicitationProperty<?>> schemaProperties,
                        String url, String elicitationId) {
        this.mode = mode;
        this.message = message;
        this.timeoutMillis = timeoutMillis;
        this.schemaProperties = schemaProperties;
        this.elicitationId = elicitationId;
        this.url = url;
    }

    /**
     * Returns the elicitation mode.
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Returns the human-readable message displayed to the user.
     */
    public String message() {
        return message;
    }

    /**
     * Returns the timeout in milliseconds to wait for the client's response (default 30 000).
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Returns the form schema properties that define the fields to collect.
     * Present only in {@link Mode#FORM} mode; {@code null} in {@link Mode#URL} mode.
     */
    public List<ElicitationProperty<?>> schemaProperties() {
        return schemaProperties;
    }

    /**
     * Returns the URL the client should open for out-of-band interaction.
     * Present only in {@link Mode#URL} mode; {@code null} in {@link Mode#FORM} mode.
     */
    public String url() {
        return url;
    }

    /**
     * Returns the correlation identifier for the out-of-band interaction, used to
     * match the {@code notifications/elicitation/complete} notification back to this request.
     * Present only in {@link Mode#URL} mode (auto-generated if not set explicitly);
     * {@code null} in {@link Mode#FORM} mode.
     */
    public String elicitationId() {
        return elicitationId;
    }

    /**
     * Creates a builder for a form-mode elicitation request.
     *
     * @param message the message displayed to the user
     */
    public static FormBuilder formBuilder(String message) {
        return new FormBuilder(message);
    }

    /**
     * Creates a builder for a URL-mode elicitation request.
     * If the {@code elicitationId} is not set on the builder, a random UUID is generated.
     *
     * @param message the message displayed to the user
     * @param url     the URL the client should open
     */
    public static UrlBuilder urlBuilder(String message, String url) {
        return new UrlBuilder(message, url);
    }

    public static final class FormBuilder {

        private final String message;
        private final List<ElicitationProperty<?>> properties = new ArrayList<>();
        private long timeoutMillis = 30_000L;

        FormBuilder(String message) {
            this.message = requireNonNull(message, ROOT_LOGGER.parameterMustNotBeNull("message"));
        }

        public StringProperty addString(String name) {
            StringProperty p = new StringProperty(name);
            properties.add(p);
            return p;
        }

        public BooleanProperty addBoolean(String name) {
            BooleanProperty p = new BooleanProperty(name);
            properties.add(p);
            return p;
        }

        public IntegerProperty addInteger(String name) {
            IntegerProperty p = new IntegerProperty(name);
            properties.add(p);
            return p;
        }

        public NumberProperty addNumber(String name) {
            NumberProperty p = new NumberProperty(name);
            properties.add(p);
            return p;
        }

        public EnumProperty addEnum(String name, String... values) {
            return addEnum(name, List.of(values));
        }

        public EnumProperty addEnum(String name, List<String> values) {
            EnumProperty p = new EnumProperty(name, values);
            properties.add(p);
            return p;
        }

        public MultiStringProperty addMultiString(String name, String... values) {
            return addMultiString(name, List.of(values));
        }

        public MultiStringProperty addMultiString(String name, List<String> values) {
            MultiStringProperty p = new MultiStringProperty(name, values);
            properties.add(p);
            return p;
        }

        public FormBuilder timeout(long millis) {
            if (millis <= 0) {
                throw ROOT_LOGGER.parameterMustBePositive("timeout");
            }
            this.timeoutMillis = millis;
            return this;
        }

        public Elicitation build() {
            if (properties.isEmpty()) {
                throw new IllegalStateException("At least one property must be added");
            }
            return new Elicitation(Mode.FORM, message, timeoutMillis,
                    List.copyOf(properties), null, null);
        }
    }

    public static final class UrlBuilder {

        private final String message;
        private final String url;
        private String elicitationId;
        private long timeoutMillis = 30_000L;

        public UrlBuilder(String message, String url) {
            this.message = requireNonNull(message, ROOT_LOGGER.parameterMustNotBeNull("message"));
            this.url = requireNonNull(url, ROOT_LOGGER.parameterMustNotBeNull("url"));
        }

        public UrlBuilder elicitationId(String elicitationId) {
            this.elicitationId = requireNonNull(elicitationId, ROOT_LOGGER.parameterMustNotBeNull("elicitationId"));
            return this;
        }

        public UrlBuilder timeout(long millis) {
            if (millis <= 0) {
                throw ROOT_LOGGER.parameterMustBePositive("timeout");
            }
            this.timeoutMillis = millis;
            return this;
        }

        public Elicitation build() {
            String id = elicitationId != null ? elicitationId: randomUUID().toString();
            return new Elicitation(Mode.URL, message, timeoutMillis,
                    null, url, id);
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

        public Optional<String> getString(String key) {
            Object v = content.get(key);
            return v instanceof String s ? Optional.of(s) : Optional.empty();
        }

        public Optional<String> getString(StringProperty property) {
            Object v = content.get(property.name());
            if (v instanceof String s) return Optional.of(s);
            return Optional.ofNullable(property.defaultValue());
        }

        public Optional<String> getString(EnumProperty property) {
            Object v = content.get(property.name());
            if (v instanceof String s) return Optional.of(s);
            return Optional.ofNullable(property.defaultValue());
        }

        public Optional<Boolean> getBoolean(String key) {
            Object v = content.get(key);
            return v instanceof Boolean b ? Optional.of(b) : Optional.empty();
        }

        public Optional<Boolean> getBoolean(BooleanProperty property) {
            Object v = content.get(property.name());
            if (v instanceof Boolean b) return Optional.of(b);
            return Optional.ofNullable(property.defaultValue());
        }

        public Optional<Integer> getInteger(String key) {
            Object v = content.get(key);
            return v instanceof Number n ? Optional.of(n.intValue()) : Optional.empty();
        }

        public Optional<Integer> getInteger(IntegerProperty property) {
            Object v = content.get(property.name());
            if (v instanceof Number n) return Optional.of(n.intValue());
            return Optional.ofNullable(property.defaultValue());
        }

        public Optional<Double> getNumber(String key) {
            Object v = content.get(key);
            return v instanceof Number n ? Optional.of(n.doubleValue()) : Optional.empty();
        }

        public Optional<Double> getNumber(NumberProperty property) {
            Object v = content.get(property.name());
            if (v instanceof Number n) return Optional.of(n.doubleValue());
            return Optional.ofNullable(property.defaultValue());
        }

        @SuppressWarnings("unchecked")
        public Optional<List<String>> getStrings(String key) {
            Object v = content.get(key);
            return v instanceof List ? Optional.of((List<String>) v) : Optional.empty();
        }

        @SuppressWarnings("unchecked")
        public Optional<List<String>> getStrings(MultiStringProperty property) {
            Object v = content.get(property.name());
            if (v instanceof List) return Optional.of((List<String>) v);
            return Optional.ofNullable(property.defaultValue());
        }
    }
}
