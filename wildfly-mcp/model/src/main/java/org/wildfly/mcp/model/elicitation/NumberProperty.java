/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.elicitation;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Schema for a decimal number elicitation property.
 * Serializes to {@code {"type":"number","minimum":...,"maximum":...}}.
 */
public final class NumberProperty implements ElicitationProperty<Double> {

    private final String name;
    private boolean required = true;
    private String title;
    private String description;
    private Double min;
    private Double max;
    private Double defaultValue;

    public NumberProperty(String name) {
        this.name = name;
    }

    public NumberProperty required(boolean required) {
        this.required = required;
        return this;
    }

    public NumberProperty optional() {
        this.required = false;
        return this;
    }

    public NumberProperty title(String title) {
        this.title = title;
        return this;
    }

    public NumberProperty description(String description) {
        this.description = description;
        return this;
    }

    public NumberProperty min(double min) {
        this.min = min;
        return this;
    }

    public NumberProperty max(double max) {
        this.max = max;
        return this;
    }

    public NumberProperty defaultValue(Double defaultValue) {
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

    public Double min() {
        return min;
    }

    public Double max() {
        return max;
    }

    @Override
    public Double defaultValue() {
        return defaultValue;
    }

    @Override
    public JsonObject jsonSchema() {
        JsonObjectBuilder b = Json.createObjectBuilder().add("type", "number");
        if (title != null) {
            b.add("title", title);
        }
        if (description != null) {
            b.add("description", description);
        }
        if (min != null) {
            b.add("minimum", min);
        }
        if (max != null) {
            b.add("maximum", max);
        }
        if (defaultValue != null) {
            b.add("default", defaultValue);
        }
        return b.build();
    }
}
