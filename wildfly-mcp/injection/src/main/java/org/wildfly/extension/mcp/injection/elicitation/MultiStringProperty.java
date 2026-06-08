/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.List;
import java.util.Objects;

/**
 * Schema for a multi-select enum elicitation property.
 *
 * <p>Without titles, serializes to {@code {"type":"array","items":{"type":"string","enum":[...]}}}.
 * With titles, serializes to {@code {"type":"array","items":{"anyOf":[{"const":"val","title":"Name"},...]}}}.</p>
 */
public final class MultiStringProperty implements ElicitationProperty<List<String>> {

    private final String name;
    private final List<String> enumValues;
    private boolean required = true;
    private String title;
    private String description;
    private List<String> enumTitles;
    private Integer minItems;
    private Integer maxItems;
    private List<String> defaultValue;

    public MultiStringProperty(String name, List<String> enumValues) {
        this.name = name;
        Objects.requireNonNull(enumValues, "enumValues must not be null");
        if (enumValues.isEmpty()) {
            throw new IllegalArgumentException("enumValues must not be empty");
        }
        this.enumValues = List.copyOf(enumValues);
    }

    public MultiStringProperty required(boolean required) {
        this.required = required;
        return this;
    }

    public MultiStringProperty optional() {
        this.required = false;
        return this;
    }

    public MultiStringProperty title(String title) {
        this.title = title;
        return this;
    }

    public MultiStringProperty description(String description) {
        this.description = description;
        return this;
    }

    public MultiStringProperty enumTitles(List<String> enumTitles) {
        Objects.requireNonNull(enumTitles, "enumTitles must not be null");
        if (enumTitles.size() != enumValues.size()) {
            throw new IllegalArgumentException("enumTitles must have the same length as enumValues");
        }
        this.enumTitles = List.copyOf(enumTitles);
        return this;
    }

    public MultiStringProperty minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    public MultiStringProperty maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    public MultiStringProperty defaultValue(List<String> defaultValue) {
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

    public Integer minItems() {
        return minItems;
    }

    public Integer maxItems() {
        return maxItems;
    }

    @Override
    public List<String> defaultValue() {
        return defaultValue;
    }

    @Override
    public JsonObject jsonSchema() {
        JsonObjectBuilder b = Json.createObjectBuilder().add("type", "array");

        if (title != null) b.add("title", title);
        if (description != null) b.add("description", description);

        if (enumTitles != null) {
            JsonArrayBuilder anyOf = Json.createArrayBuilder();
            for (int i = 0; i < enumValues.size(); i++) {
                anyOf.add(Json.createObjectBuilder()
                        .add("const", enumValues.get(i))
                        .add("title", enumTitles.get(i)));
            }
            b.add("items", Json.createObjectBuilder().add("anyOf", anyOf));
        } else {
            JsonArrayBuilder values = Json.createArrayBuilder();
            for (String v : enumValues) {
                values.add(v);
            }
            b.add("items", Json.createObjectBuilder()
                    .add("type", "string")
                    .add("enum", values));
        }

        if (minItems != null) b.add("minItems", minItems);
        if (maxItems != null) b.add("maxItems", maxItems);

        if (defaultValue != null) {
            JsonArrayBuilder defaults = Json.createArrayBuilder();
            for (String v : defaultValue) {
                defaults.add(v);
            }
            b.add("default", defaults);
        }
        return b.build();
    }
}
