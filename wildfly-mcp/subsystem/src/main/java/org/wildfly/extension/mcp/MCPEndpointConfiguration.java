/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import java.util.Set;

/**
 * Encapsulates the configuration of an MCP Server endpoint.
 * @param ssePath: the URL path of the sse endpoint.
 * @param messagesPath: the URL path of the messages' endpoint.
 * @param streamablePath: the URL path of the streamable endpoint.
 * @param pageSize: maximum number of items per paginated list response; 0 disables pagination.
 * @param timeout: idle connection timeout in seconds; connections inactive for longer than this will be closed.
 * @param allowedOrigins: additional hostnames accepted by DNS-rebinding validation; empty means auto-detect only.
 * @param cacheTtlMs: default cache TTL in milliseconds for list and discover responses.
 * @param cacheScope: default cache scope ("public" or "private") for list and discover responses.
 */
public record MCPEndpointConfiguration(String ssePath, String messagesPath, String streamablePath, int pageSize, long timeout, String requestStateSecret, Set<String> allowedOrigins, long cacheTtlMs, String cacheScope) {

    public MCPEndpointConfiguration(String ssePath, String messagesPath, String streamablePath, int pageSize, long timeout) {
        this(ssePath, messagesPath, streamablePath, pageSize, timeout, null, Set.of(), 3_600_000L, "public");
    }
}