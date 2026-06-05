/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import jakarta.json.JsonObjectBuilder;

/**
 * Marker interface for MCP elicitation schema property types.
 * Each implementation serializes itself to JSON Schema via {@link #asJson()}.
 */
public sealed interface PrimitiveSchema permits BooleanSchema, EnumSchema, IntegerSchema, NumberSchema, StringSchema {

    boolean required();

    /**
     * Returns a {@link JsonObjectBuilder} containing the JSON Schema representation
     * of this property. The caller is responsible for calling {@code .build()}.
     */
    JsonObjectBuilder asJson();
}
