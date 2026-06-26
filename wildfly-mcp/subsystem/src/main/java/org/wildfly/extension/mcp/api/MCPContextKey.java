/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

/**
 * A typed key for storing per-request state in {@link MCPMessageContext#setAttribute}.
 * <p>
 * Keys use identity equality — two distinct {@code MCPContextKey} instances with the same
 * name are different keys. Declare keys as {@code static final} constants so that
 * {@code setAttribute} and {@code getAttribute} always use the same instance.
 * </p>
 *
 * @param <T> the type of the value associated with this key
 */
public final class MCPContextKey<T> {

    private final String name;

    private MCPContextKey(String name) {
        this.name = name;
    }

    public static <T> MCPContextKey<T> of(String name) {
        return new MCPContextKey<>(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
