/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Schema for a boolean elicitation property.
 * Serializes to {@code {"type":"boolean"}}.
 */
public final class BooleanProperty implements ElicitationProperty<Boolean> {

    private final String name;
    private boolean required = true;
    private String title;
    private String description;
    private Boolean defaultValue;

    public BooleanProperty(String name) {
        this.name = name;
    }

    public BooleanProperty required(boolean required) {
        this.required = required;
        return this;
    }

    public BooleanProperty optional() {
        this.required = false;
        return this;
    }

    public BooleanProperty title(String title) {
        this.title = title;
        return this;
    }

    public BooleanProperty description(String description) {
        this.description = description;
        return this;
    }

    public BooleanProperty defaultValue(Boolean defaultValue) {
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

    @Override
    public Boolean defaultValue() {
        return defaultValue;
    }

    @Override
    public JsonObject jsonSchema() {
        JsonObjectBuilder b = Json.createObjectBuilder().add("type", "boolean");
        if (title != null) {
            b.add("title", title);
        }
        if (description != null) {
            b.add("description", description);
        }
        if (defaultValue != null) {
            b.add("default", defaultValue);
        }
        return b.build();
    }
}
