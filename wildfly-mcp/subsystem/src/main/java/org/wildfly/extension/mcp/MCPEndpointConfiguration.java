/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

/**
 * Encapsulates the configuration of an MCP Server endpoint.
 * @param ssePath: the URL path of the sse endpoint.
 * @param messagesPath: the URL path of the messages' endpoint.
 * @param streamablePath: the URL path of the streamable endpoint.
 * @param pageSize: maximum number of items per paginated list response; 0 disables pagination.
 * @param timeout: idle connection timeout in seconds; connections inactive for longer than this will be closed.
 */
public record MCPEndpointConfiguration(String ssePath, String messagesPath, String streamablePath, int pageSize, long timeout) {
}