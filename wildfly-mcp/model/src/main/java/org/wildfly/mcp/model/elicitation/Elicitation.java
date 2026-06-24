/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.elicitation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.wildfly.mcp.model.MCPModelLogger.ROOT_LOGGER;

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
     *
     * <p>The {@code action} indicates whether the user accepted, declined, or cancelled
     * the elicitation. The {@code content} map holds the values entered by the user
     * (keyed by property name). A {@code null} content is normalised to an empty map.</p>
     *
     * <p>Type-safe accessors ({@link #getString}, {@link #getBoolean}, {@link #getInteger},
     * {@link #getNumber}, {@link #getStrings}) return {@link Optional#empty()} when the
     * key is absent or the value has an unexpected type. Property-typed overloads fall back
     * to the property's default value when the key is absent.</p>
     */
    public record Response(Action action, Map<String, Object> content) {

        /** The action the user took in response to the elicitation. */
        public enum Action {
            ACCEPT,
            DECLINE,
            CANCEL
        }

        public Response(Action action, Map<String, Object> content) {
            this.action = requireNonNull(action, ROOT_LOGGER.parameterMustNotBeNull("action"));
            this.content = content != null ? Collections.unmodifiableMap(content) : Collections.emptyMap();
        }

        /** Returns {@code true} if the user accepted the elicitation. */
        public boolean isAccepted() {
            return action == Action.ACCEPT;
        }

        /** Returns {@code true} if the user declined the elicitation. */
        public boolean isDeclined() {
            return action == Action.DECLINE;
        }

        /** Returns {@code true} if the user cancelled the elicitation. */
        public boolean isCancelled() {
            return action == Action.CANCEL;
        }

        /**
         * Returns the string value for the given key, or empty if absent or not a string.
         */
        public Optional<String> getString(String key) {
            Object v = content.get(key);
            return v instanceof String s ? Optional.of(s) : Optional.empty();
        }

        /**
         * Returns the string value for the given property, falling back to its
         * {@linkplain StringProperty#defaultValue() default} when the key is absent.
         */
        public Optional<String> getString(StringProperty property) {
            Object v = content.get(property.name());
            if (v instanceof String s) {
                return Optional.of(s);
            }
            if (!content.containsKey(property.name())) {
                return Optional.ofNullable(property.defaultValue());
            }
            return Optional.empty();
        }

        /**
         * Returns the string value for the given enum property, falling back to its
         * {@linkplain EnumProperty#defaultValue() default} when the key is absent.
         */
        public Optional<String> getString(EnumProperty property) {
            Object v = content.get(property.name());
            if (v instanceof String s) {
                return Optional.of(s);
            }
            if (!content.containsKey(property.name())) {
                return Optional.ofNullable(property.defaultValue());
            }
            return Optional.empty();
        }

        /**
         * Returns the boolean value for the given key, or empty if absent or not a boolean.
         */
        public Optional<Boolean> getBoolean(String key) {
            Object v = content.get(key);
            return v instanceof Boolean b ? Optional.of(b) : Optional.empty();
        }

        /**
         * Returns the boolean value for the given property, falling back to its
         * {@linkplain BooleanProperty#defaultValue() default} when the key is absent.
         */
        public Optional<Boolean> getBoolean(BooleanProperty property) {
            Object v = content.get(property.name());
            if (v instanceof Boolean b) {
                return Optional.of(b);
            }
            if (!content.containsKey(property.name())) {
                return Optional.ofNullable(property.defaultValue());
            }
            return Optional.empty();
        }

        /**
         * Returns the integer value for the given key, or empty if absent or not an integer.
         */
        public Optional<Integer> getInteger(String key) {
            Object v = content.get(key);
            return v instanceof Integer i ? Optional.of(i) : Optional.empty();
        }

        /**
         * Returns the integer value for the given property, falling back to its
         * {@linkplain IntegerProperty#defaultValue() default} when the key is absent.
         */
        public Optional<Integer> getInteger(IntegerProperty property) {
            Object v = content.get(property.name());
            if (v instanceof Integer i) {
                return Optional.of(i);
            }
            if (!content.containsKey(property.name())) {
                return Optional.ofNullable(property.defaultValue());
            }
            return Optional.empty();
        }

        /**
         * Returns the numeric value for the given key, or empty if absent or not a number.
         */
        public Optional<Double> getNumber(String key) {
            Object v = content.get(key);
            return v instanceof Number n ? Optional.of(n.doubleValue()) : Optional.empty();
        }

        /**
         * Returns the numeric value for the given property, falling back to its
         * {@linkplain NumberProperty#defaultValue() default} when the key is absent.
         */
        public Optional<Double> getNumber(NumberProperty property) {
            Object v = content.get(property.name());
            if (v instanceof Number n) {
                return Optional.of(n.doubleValue());
            }
            if (!content.containsKey(property.name())) {
                return Optional.ofNullable(property.defaultValue());
            }
            return Optional.empty();
        }

        /**
         * Returns the list of strings for the given key, or empty if absent, not a list,
         * or contains non-string elements.
         */
        public Optional<List<String>> getStrings(String key) {
            Object v = content.get(key);
            return asStringList(v);
        }

        /**
         * Returns the list of strings for the given property, falling back to its
         * {@linkplain MultiStringProperty#defaultValue() default} when the key is absent.
         * Returns empty if the value is not a list or contains non-string elements.
         */
        public Optional<List<String>> getStrings(MultiStringProperty property) {
            Object v = content.get(property.name());
            if (v == null && !content.containsKey(property.name())) {
                return Optional.ofNullable(property.defaultValue());
            }
            return asStringList(v);
        }

        private static Optional<List<String>> asStringList(Object v) {
            if (v instanceof List<?> list) {
                for (Object element : list) {
                    if (!(element instanceof String)) {
                        return Optional.empty();
                    }
                }
                @SuppressWarnings("unchecked")
                List<String> strings = (List<String>) list;
                return Optional.of(strings);
            }
            return Optional.empty();
        }
    }
}
