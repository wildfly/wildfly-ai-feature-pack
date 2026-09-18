/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_SESSION_ID_HEADER;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.MCPConnection;

public class MCPStreamableConnectionCallBack implements ServerSentEventConnectionCallback {

    private final ConnectionManager connectionManager;
    private final MCPMessageHandler handler;

    public MCPStreamableConnectionCallBack(ConnectionManager connectionManager, MCPMessageHandler handler) {
        this.connectionManager = connectionManager;
        this.handler = handler;
    }

    @Override
    public void connected(ServerSentEventConnection sseConnection, String lastEventId) {
        String id = sseConnection.getResponseHeaders().getFirst(MCP_SESSION_ID_HEADER);
        if (id == null) {
            return;
        }
        ConnectionManager.PendingMessage pending = connectionManager.takePending(id);
        if (pending == null) {
            // GET notification stream — register with existing session's responder
            MCPConnection existing = connectionManager.get(id);
            if (existing instanceof ServerSentEventResponder responder) {
                ROOT_LOGGER.debugf("Registering additional SSE stream for session [%s]", id);
                responder.addNotificationStream(sseConnection);
            }
            return;
        }
        // POST — create the primary responder and process the initial message
        ROOT_LOGGER.debugf("Client connection initialized [%s]", id);
        ServerSentEventResponder connection = new ServerSentEventResponder(sseConnection, id);
        connectionManager.add(connection);
        sseConnection.addCloseTask(channel -> {
            ROOT_LOGGER.debugf("SSE channel closed, cleaning up connection [%s]", id);
            connection.cancel();
            handler.cleanupConnection(connection);
            connectionManager.remove(id);
        });
        ROOT_LOGGER.debugf("Received message from client: %s", pending.content());
        JsonRPC.validate(pending.content(), connection);
        handler.handle(pending.content(), connection, connection,
                pending.clientAddress(), pending.clientPort(),
                pending.networkProtocolVersion(), pending.mcpHeaders());
    }

}
