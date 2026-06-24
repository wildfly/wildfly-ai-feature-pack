/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.tool;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies a custom input schema generator for an MCP tool method.
 * <p>
 * When present on a method annotated with {@code @Tool}, the generated JSON Schema
 * replaces the auto-generated input schema derived from {@code @ToolArg} parameters.
 * The referenced class must implement {@code ToolSchemaGenerator}.
 * </p>
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface InputSchema {

    /**
     * A class implementing {@code ToolSchemaGenerator} whose {@code generate()} method
     * returns a JSON Schema string for the tool's input.
     */
    Class<?> generator();
}
