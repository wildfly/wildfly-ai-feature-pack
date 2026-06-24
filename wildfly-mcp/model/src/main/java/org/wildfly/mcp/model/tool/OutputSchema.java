/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.model.tool;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies a custom output schema for an MCP tool method.
 * <p>
 * When present on a method annotated with {@code @Tool}, either a custom generator or
 * a class to derive the schema from can be specified. If {@code generator()} is set,
 * its {@code generate()} method produces the JSON Schema directly. If {@code from()} is set,
 * the schema is generated from that class instead of the method's return type.
 * </p>
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface OutputSchema {

    /**
     * A class implementing {@code ToolSchemaGenerator} whose {@code generate()} method
     * returns a JSON Schema string for the tool's output.
     * Defaults to {@code void.class} (not set).
     */
    Class<?> generator() default void.class;

    /**
     * A class to generate the output schema from, instead of the method's return type.
     * Defaults to {@code void.class} (not set).
     */
    Class<?> from() default void.class;
}
