/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.List;
import org.wildfly.extension.mcp.api.MCPMessageListener;

/**
 * Optional configuration for {@link MCPMessageHandler}. Collects the optional parameters
 * that would otherwise proliferate as constructor overloads.
 */
public record MCPHandlerConfig(
        int pageSize,
        List<MCPMessageListener> listeners,
        String requestStateSecret,
        long cacheTtlMs,
        String cacheScope) {

    public static final MCPHandlerConfig DEFAULT = new MCPHandlerConfig(0, List.of(), null, 3_600_000L, "public");

    public MCPHandlerConfig {
        listeners = listeners != null ? List.copyOf(listeners) : List.of();
        cacheScope = cacheScope != null ? cacheScope : "public";
    }
}
