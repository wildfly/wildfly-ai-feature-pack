package org.wildfly.extension.mcp.server;

import static io.undertow.util.Headers.ALLOW;
import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;


public class MessagesHttpHandler implements HttpHandler {
    private final ConnectionManager connectionManager;
    private final MCPMessageHandler handler;

    public MessagesHttpHandler(ConnectionManager connectionManager, MCPMessageHandler handler) {
        this.connectionManager = connectionManager;
        this.handler = handler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
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
        if (connectionId == null) {
            ROOT_LOGGER.connectionIdMissing(exchange.getRequestPath());
            exchange.setStatusCode(400);
            return;
        }
        ServerSentEventResponder connection = (ServerSentEventResponder)connectionManager.get(connectionId);
        exchange.startBlocking();
        JsonReader reader = Json.createReader(exchange.getInputStream());
        JsonObject content = reader.readObject();
        ROOT_LOGGER.debugf("Received message from client: %s", content);
        JsonRPC.validate(content, connection);
        java.net.InetSocketAddress src = exchange.getSourceAddress();
        String clientAddress = src != null ? src.getHostString() : null;
        int clientPort = src != null ? src.getPort() : -1;
        handler.handle(content, connection, connection,
                clientAddress, clientPort, MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()));
    }
}
