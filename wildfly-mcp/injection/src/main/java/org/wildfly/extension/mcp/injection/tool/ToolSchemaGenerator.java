/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

/**
 * Generates a JSON Schema for a tool's input or output.
 * <p>
 * Implementations are referenced by class name in {@code @Tool.InputSchema(generator=...)}
 * and {@code @Tool.OutputSchema(generator=...)}, and must be CDI beans.
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
