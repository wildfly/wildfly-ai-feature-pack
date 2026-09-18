/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp.conformance;

import org.wildfly.extension.mcp.injection.tool.ToolSchemaGenerator;

public class JsonSchema2020_12SchemaGenerator implements ToolSchemaGenerator {

    @Override
    public String generate() {
        return """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "$defs": {
                    "address": {
                      "$anchor": "addressDef",
                      "type": "object",
                      "properties": {
                        "street": { "type": "string" },
                        "city": { "type": "string" }
                      }
                    }
                  },
                  "properties": {
                    "name": { "type": "string" },
                    "address": { "$ref": "#/$defs/address" },
                    "contactMethod": {
                      "type": "string",
                      "enum": ["phone", "email"]
                    },
                    "phone": { "type": "string" },
                    "email": { "type": "string" }
                  },
                  "allOf": [
                    {
                      "anyOf": [
                        { "required": ["phone"] },
                        { "required": ["email"] }
                      ]
                    }
                  ],
                  "if": {
                    "properties": {
                      "contactMethod": { "const": "phone" }
                    },
                    "required": ["contactMethod"]
                  },
                  "then": { "required": ["phone"] },
                  "else": { "required": ["email"] },
                  "additionalProperties": false
                }
                """;
    }
}
