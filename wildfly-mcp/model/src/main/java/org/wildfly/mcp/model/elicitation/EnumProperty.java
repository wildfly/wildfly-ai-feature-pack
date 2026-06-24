/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.elicitation;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import java.util.List;
import java.util.Objects;

import static org.wildfly.mcp.model.MCPModelLogger.ROOT_LOGGER;

/**
 * Schema for a single-select enum elicitation property.
 *
 * <p>Without titles, serializes to {@code {"type":"string","enum":[...]}}.
 * With titles, serializes to {@code {"type":"string","oneOf":[{"const":"val","title":"Name"},...]}}.</p>
 */
public final class EnumProperty implements ElicitationProperty<String> {

    private final String name;
    private final List<String> enumValues;
    private boolean required = true;
    private String title;
    private String description;
    private List<String> enumTitles;
    private String defaultValue;

    public EnumProperty(String name, List<String> enumValues) {
        this.name = name;
        Objects.requireNonNull(enumValues, ROOT_LOGGER.parameterMustNotBeNull("enumValues"));
        if (enumValues.isEmpty()) {
            throw ROOT_LOGGER.parameterMustNotBeEmpty("enumValues");
        }
        this.enumValues = List.copyOf(enumValues);
    }

    public EnumProperty required(boolean required) {
        this.required = required;
        return this;
    }

    public EnumProperty optional() {
        this.required = false;
        return this;
    }

    public EnumProperty title(String title) {
        this.title = title;
        return this;
    }

    public EnumProperty description(String description) {
        this.description = description;
        return this;
    }

    public EnumProperty enumTitles(List<String> enumTitles) {
        Objects.requireNonNull(enumTitles, ROOT_LOGGER.parameterMustNotBeNull("enumTitles"));
        if (enumTitles.size() != enumValues.size()) {
            throw ROOT_LOGGER.parameterMustHaveSameSize("enumTitles", "enumValues");
        }
        this.enumTitles = List.copyOf(enumTitles);
        return this;
    }

    public EnumProperty defaultValue(String defaultValue) {
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

    public List<String> enumValues() {
        return enumValues;
    }

    public List<String> enumTitles() {
        return enumTitles;
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

        if (enumTitles != null) {
            JsonArrayBuilder oneOf = Json.createArrayBuilder();
            for (int i = 0; i < enumValues.size(); i++) {
                oneOf.add(Json.createObjectBuilder()
                        .add("const", enumValues.get(i))
                        .add("title", enumTitles.get(i)));
            }
            b.add("oneOf", oneOf);
        } else {
            JsonArrayBuilder values = Json.createArrayBuilder();
            for (String v : enumValues) {
                values.add(v);
            }
            b.add("enum", values);
        }

        if (defaultValue != null) {
            b.add("default", defaultValue);
        }
        return b.build();
    }
}
