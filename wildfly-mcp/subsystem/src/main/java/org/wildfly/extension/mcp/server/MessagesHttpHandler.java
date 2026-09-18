package org.wildfly.extension.mcp.server;

import static io.undertow.util.Headers.ALLOW;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.server.MCPServerUtils.JSON_CONTENT_TYPE;
import static org.wildfly.extension.mcp.server.MCPServerUtils.asJsonObject;

import java.util.Collections;
import java.util.Set;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;


public class MessagesHttpHandler implements HttpHandler {
    private final ConnectionManager connectionManager;
    private final MCPMessageHandler handler;
    private final Set<String> allowedOrigins;

    public MessagesHttpHandler(ConnectionManager connectionManager, MCPMessageHandler handler) {
        this(connectionManager, handler, Collections.emptySet());
    }

    public MessagesHttpHandler(ConnectionManager connectionManager, MCPMessageHandler handler, Set<String> allowedOrigins) {
        this.connectionManager = connectionManager;
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (!MCPServerUtils.validateOrigin(exchange, allowedOrigins)) {
            return;
        }
        if(! Methods.POST.equals(exchange.getRequestMethod())) {
            exchange.setStatusCode(405).getResponseHeaders().add(ALLOW, Methods.POST_STRING);
            exchange.endExchange();
            return;
        }
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        String connectionId = exchange.getRequestPath().substring( exchange.getRequestPath().lastIndexOf('/') + 1);
        if (connectionId == null || connectionId.isEmpty()) {
            ROOT_LOGGER.connectionIdMissing(exchange.getRequestPath());
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
            exchange.getResponseSender().send(
                    org.wildfly.extension.mcp.api.Messages.newError(null, JsonRPC.INVALID_REQUEST,
                            "Missing connection ID").toString());
            return;
        }
        ServerSentEventResponder connection = (ServerSentEventResponder)connectionManager.get(connectionId);
        if (connection == null) {
            ROOT_LOGGER.unknownSession(connectionId);
            exchange.setStatusCode(404);
            exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
            exchange.getResponseSender().send(
                    org.wildfly.extension.mcp.api.Messages.newError(null, JsonRPC.INVALID_REQUEST,
                            "Unknown session: " + connectionId).toString());
            return;
        }
        exchange.startBlocking();
        JsonValue parsed;
        try {
            JsonReader reader = Json.createReader(exchange.getInputStream());
            parsed = reader.read();
        } catch (Exception e) {
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
            exchange.getResponseSender().send(
                    org.wildfly.extension.mcp.api.Messages.newError(null, JsonRPC.INVALID_REQUEST,
                            "Invalid JSON").toString());
            return;
        }
        JsonObject content = asJsonObject(parsed);
        if (content == null) {
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
            exchange.getResponseSender().send(
                    org.wildfly.extension.mcp.api.Messages.newError(null, JsonRPC.INVALID_REQUEST,
                            "Expected a JSON object").toString());
            return;
        }
        ROOT_LOGGER.debugf("Received message from client: %s", content);
        JsonRPC.validate(content, connection);
        java.net.InetSocketAddress src = exchange.getSourceAddress();
        String clientAddress = src != null ? src.getHostString() : null;
        int clientPort = src != null ? src.getPort() : -1;
        handler.handle(content, connection, connection,
                clientAddress, clientPort, MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                MCPServerUtils.extractMcpHeaders(exchange));
    }
}
