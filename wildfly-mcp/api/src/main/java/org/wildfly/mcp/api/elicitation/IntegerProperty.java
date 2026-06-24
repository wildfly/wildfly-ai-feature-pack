/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.elicitation;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.wildfly.mcp.api.MCPApiLogger;

/**
 * Schema for an integer elicitation property.
 * Serializes to {@code {"type":"integer","minimum":...,"maximum":...}}.
 */
public final class IntegerProperty implements ElicitationProperty<Integer> {

    private final String name;
    private boolean required = true;
    private String title;
    private String description;
    private Integer min;
    private Integer max;
    private Integer defaultValue;

    public IntegerProperty(String name) {
        this.name = name;
    }

    public IntegerProperty required(boolean required) {
        this.required = required;
        return this;
    }

    public IntegerProperty optional() {
        this.required = false;
        return this;
    }

    public IntegerProperty title(String title) {
        this.title = title;
        return this;
    }

    public IntegerProperty description(String description) {
        this.description = description;
        return this;
    }

    public IntegerProperty min(int min) {
        this.min = min;
        if (max != null && min > max) {
          throw MCPApiLogger.ROOT_LOGGER.maxCanNotBeLessThanMin(max, min);
        }
        return this;
    }

    public IntegerProperty max(int max) {
        this.max = max;
        if (min != null && min > max) {
            throw MCPApiLogger.ROOT_LOGGER.maxCanNotBeLessThanMin(max, min);
        }
        return this;
    }

    public IntegerProperty defaultValue(Integer defaultValue) {
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

    public Integer min() {
        return min;
    }

    public Integer max() {
        return max;
    }

    @Override
    public Integer defaultValue() {
        return defaultValue;
    }

    @Override
    public JsonObject jsonSchema() {
        JsonObjectBuilder b = Json.createObjectBuilder().add("type", "integer");
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
