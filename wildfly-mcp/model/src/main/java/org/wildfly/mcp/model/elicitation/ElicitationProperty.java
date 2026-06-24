/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.elicitation;

import jakarta.json.JsonObject;

/**
 * Sealed interface for MCP elicitation schema property types.
 * Each implementation serializes itself to JSON Schema via {@link #jsonSchema()}.
 *
 * @param <T> the Java type of this property
 */
public sealed interface ElicitationProperty<T> permits BooleanProperty, EnumProperty, IntegerProperty, MultiStringProperty, NumberProperty, StringProperty {

    String name();

    boolean required();

    String title();

    String description();

    T defaultValue();

    /**
     * Returns a {@link JsonObject} containing the JSON Schema representation
     * of this property.
     */
    JsonObject jsonSchema();
}
