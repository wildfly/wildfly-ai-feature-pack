/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

/**
 * Generates a JSON Schema for a tool's input or output.
 * <p>
 * Implementations are referenced by class in {@code @InputSchema(generator=...)}
 * and {@code @OutputSchema(generator=...)}. They can be CDI beans or plain classes
 * with a no-arg constructor.
 * </p>
 */
@FunctionalInterface
public interface ToolSchemaGenerator {

    /**
     * Returns a JSON Schema as a string.
     *
     * @return a valid JSON Schema document
     */
    String generate();
}
