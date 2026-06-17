/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_SESSION_ID_HEADER;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import io.undertow.util.AttachmentKey;
import jakarta.json.JsonObject;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;

public class MCPStreamableConnectionCallBack implements ServerSentEventConnectionCallback {
    public static final AttachmentKey<JsonObject> JSON_PAYLOAD = AttachmentKey.create(JsonObject.class);
    public static final AttachmentKey<String> SESSION_ID = AttachmentKey.create(String.class);

    private final ConnectionManager connectionManager;

    public MCPStreamableConnectionCallBack(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void connected(ServerSentEventConnection sseConnection, String lastEventId) {
        String id = sseConnection.getAttachment(SESSION_ID);
        ROOT_LOGGER.debugf("Client connection initialized [%s]", id);
        sseConnection.getResponseHeaders().add(MCP_SESSION_ID_HEADER, id);
        ServerSentEventResponder connection = new ServerSentEventResponder(sseConnection, id);
        connectionManager.add(connection);
        JsonObject content = sseConnection.getAttachment(JSON_PAYLOAD);
        ROOT_LOGGER.debugf("Received message from client: %s", content);
        JsonRPC.validate(content, connection);
        StreamableHttpHandler.handler.handle(content, connection, connection);
    }

}
