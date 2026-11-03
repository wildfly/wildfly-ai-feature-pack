/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import java.time.Duration;
import java.util.Set;

/**
 * Encapsulates the configuration of an MCP Server endpoint.
 * @param ssePath: the URL path of the sse endpoint.
 * @param messagesPath: the URL path of the messages' endpoint.
 * @param streamablePath: the URL path of the streamable endpoint.
 * @param pageSize: maximum number of items per paginated list response; 0 disables pagination.
 * @param timeout: idle connection timeout in seconds; connections inactive for longer than this will be closed.
 * @param requestTimeoutSeconds: maximum time in seconds to wait for a tool/request to complete before returning a timeout error.
 * @param requestStateSecret: raw HMAC key bytes used to sign requestState tokens, resolved from the
 *     {@code credential-reference} attribute's {@code SecretKeyCredential}; {@code null} disables
 *     requestState signing/verification.
 * @param allowedOrigins: additional hostnames accepted by DNS-rebinding validation; empty means auto-detect only.
 * @param cacheTtlMs: default cache TTL in milliseconds for list and discover responses.
 * @param cacheScope: default cache scope ("public" or "private") for list and discover responses.
 */
public record MCPEndpointConfiguration(String ssePath, String messagesPath, String streamablePath, int pageSize, long timeout, Duration requestTimeout, byte[] requestStateSecret, Set<String> allowedOrigins, Duration cacheTtlMs, CacheScope cacheScope) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration DEFAULT_CACHE_TIME_TO_LIVE = Duration.ofMillis(3_600_000);
    
    public MCPEndpointConfiguration(String ssePath, String messagesPath, String streamablePath, int pageSize, long timeout) {
        this(ssePath, messagesPath, streamablePath, pageSize, timeout, DEFAULT_REQUEST_TIMEOUT, null, Set.of(), DEFAULT_CACHE_TIME_TO_LIVE, CacheScope.PUBLIC);
    }
}