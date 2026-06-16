/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_SESSION_ID_HEADER;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.api.ConnectionManager;

public class MCPServerSentConnectionCallBack implements ServerSentEventConnectionCallback {

    private final String endpoint;
    private final ConnectionManager connectionManager;

    public MCPServerSentConnectionCallBack(String endpoint, ConnectionManager connectionManager) {
        this.endpoint = endpoint;
        this.connectionManager = connectionManager;
    }

    @Override
    public void connected(ServerSentEventConnection sseConnection, String lastEventId) {
        String id = connectionManager.id();
        ROOT_LOGGER.debugf("Client connection initialized [%s]", id);
        String endpointPath = endpoint + '/' + id;
        sseConnection.getResponseHeaders().add(MCP_SESSION_ID_HEADER, id);
        ServerSentEventResponder responder = new ServerSentEventResponder(sseConnection, id);
        connectionManager.add(responder);
        ROOT_LOGGER.debugf("Sending endpoint [%s]", endpointPath);
        responder.send("endpoint", endpointPath);
    }

}
