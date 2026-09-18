/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a tool method parameter as sourced from an MCP transport header.
 * <p>
 * This annotation is only supported on {@code @Tool} methods. Resource and prompt
 * methods do not support parameter injection.
 * </p>
 * <p>
 * Values are extracted from {@code x-mcp-header-<name>} HTTP request headers
 * or from {@code params._meta.headers.<name>} in the JSON-RPC payload.
 * The HTTP header takes precedence when both are present.
 * </p>
 */
@Retention(RUNTIME)
@Target(PARAMETER)
@Documented
public @interface McpHeader {

    /**
     * The header name (without the {@code x-mcp-header-} prefix).
     */
    String value();

    /**
     * Whether this header is required. When {@code true} and the header is missing,
     * a {@code -32602 InvalidParams} error is returned.
     */
    boolean required() default false;
}
