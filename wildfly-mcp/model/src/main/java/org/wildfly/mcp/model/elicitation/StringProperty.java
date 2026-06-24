/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.elicitation;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Schema for a string elicitation property.
 * Serializes to {@code {"type":"string",...}}.
 */
public final class StringProperty implements ElicitationProperty<String> {

    /**
     * Supported format hints for string properties, as defined by the MCP elicitation spec.
     */
    public enum Format {
        EMAIL("email"),
        URI("uri"),
        DATE("date"),
        DATE_TIME("date-time");

        private final String value;

        Format(String value) {
            this.value = value;
        }

        /**
         * Returns the JSON Schema format string (e.g. {@code "email"}, {@code "date-time"}).
         */
        public String value() {
            return value;
        }
    }

    private final String name;
    private boolean required = true;
    private String title;
    private String description;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private Format format;
    private String defaultValue;

    public StringProperty(String name) {
        this.name = name;
    }

    public StringProperty required(boolean required) {
        this.required = required;
        return this;
    }

    public StringProperty optional() {
        this.required = false;
        return this;
    }

    public StringProperty title(String title) {
        this.title = title;
        return this;
    }

    public StringProperty description(String description) {
        this.description = description;
        return this;
    }

    public StringProperty minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    public StringProperty maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public StringProperty pattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public StringProperty format(Format format) {
        this.format = format;
        return this;
    }

    public StringProperty defaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean required() {
        return required;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }

    public Integer minLength() {
        return minLength;
    }

    public Integer maxLength() {
        return maxLength;
    }

    public String pattern() {
        return pattern;
    }

    public Format format() {
        return format;
    }

    @Override
    public String defaultValue() {
        return defaultValue;
    }

    @Override
    public JsonObject jsonSchema() {
        JsonObjectBuilder b = Json.createObjectBuilder().add("type", "string");
        if (title != null) {
            b.add("title", title);
        }
        if (description != null) {
            b.add("description", description);
        }
        if (minLength != null) {
            b.add("minLength", minLength);
        }
        if (maxLength != null) {
            b.add("maxLength", maxLength);
        }
        if (pattern != null) {
            b.add("pattern", pattern);
        }
        if (format != null) {
            b.add("format", format.value());
        }
        if (defaultValue != null) {
            b.add("default", defaultValue);
        }
        return b.build();
    }
}
