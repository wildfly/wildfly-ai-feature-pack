package org.wildfly.extension.mcp.server;

import static io.undertow.util.Headers.ALLOW;
import static io.undertow.util.Headers.CACHE_CONTROL;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.HttpString.tryFromString;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_PROTOCOL_VERSION_HEADER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_SESSION_ID_HEADER;
import static org.wildfly.extension.mcp.server.MCPMessageHandler.PROTOCOL_VERSION;
import static org.wildfly.extension.mcp.server.MCPStreamableConnectionCallBack.JSON_PAYLOAD;
import static org.wildfly.extension.mcp.server.MCPStreamableConnectionCallBack.SESSION_ID;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.util.Arrays;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;

public class StreamableHttpHandler implements HttpHandler {

    private final ConnectionManager connectionManager;
    static MCPMessageHandler handler;
    private final ServerSentEventHandler sseHandler;

    public StreamableHttpHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader,
            String serverName, String applicationName, ServerSentEventHandler sseHandler) {
        this(connectionManager, registry, classLoader, serverName, applicationName, sseHandler, 0);
    }

    public StreamableHttpHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader,
            String serverName, String applicationName, ServerSentEventHandler sseHandler, int pageSize) {
        this.connectionManager = connectionManager;
        this.sseHandler = sseHandler;
        handler = new MCPMessageHandler(connectionManager, registry, classLoader, serverName, applicationName, pageSize);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (Methods.GET.equals(exchange.getRequestMethod())) {
            this.sseHandler.handleRequest(exchange);
            return;
        }
        if (!Methods.POST.equals(exchange.getRequestMethod())) {
            ROOT_LOGGER.invalidHttpMethod(exchange.getRequestMethod().toString());
            exchange.setStatusCode(405).getResponseHeaders().add(ALLOW, Methods.POST_STRING);
            exchange.endExchange();
            return;
        }
        HeaderValues accepts = exchange.getRequestHeaders().get(Headers.ACCEPT);
        if (!isValidAcceptHeader(accepts)) {
            ROOT_LOGGER.invalidAcceptHeaders(Arrays.toString(accepts.toArray()));
            exchange.setStatusCode(400);
            exchange.endExchange();
            return;
        }

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        exchange.startBlocking();
        JsonReader reader = Json.createReader(exchange.getInputStream());
        JsonObject content = reader.readObject();
        ROOT_LOGGER.debug("Received message from client: %s".formatted(content));
        String connectionId = exchange.getRequestHeaders().getFirst(MCP_SESSION_ID_HEADER);
        if (connectionId == null) {
            connectionId = connectionManager.id();
            exchange.putAttachment(SESSION_ID, connectionId);
            exchange.putAttachment(JSON_PAYLOAD, content);
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(MCP_SESSION_ID_HEADER, connectionId);
            exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
            exchange.getResponseHeaders().put(tryFromString("Access-Control-Allow-Origin"), "*");
            exchange.getResponseHeaders().put(tryFromString("Access-Control-Expose-Headers"), "mcp-session-id");
            exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
            this.sseHandler.handleRequest(exchange);
            return;
        }
        String protocolVersion = exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION_HEADER);
        if (protocolVersion != null && !PROTOCOL_VERSION.equals(protocolVersion)) {
            MCPLogger.ROOT_LOGGER.invalidProtocolVersion(PROTOCOL_VERSION, protocolVersion);
            exchange.setStatusCode(400);
            exchange.endExchange();
            return;
        }
        ServerSentEventResponder connection = (ServerSentEventResponder) connectionManager.get(connectionId);
        JsonRPC.validate(content, connection);
        exchange.getResponseHeaders().put(MCP_SESSION_ID_HEADER, connectionId);
        exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
        exchange.getResponseHeaders().put(tryFromString("Access-Control-Allow-Origin"), "*");
        exchange.getResponseHeaders().put(tryFromString("Access-Control-Expose-Headers"), "mcp-session-id");
        exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
        handler.handle(content, connection, connection);
    }

    private boolean isValidAcceptHeader(HeaderValues accepts) {
        for (String accept : accepts) {
            if (accept.contains("application/json") && accept.contains("text/event-stream")) {
                return true;
            }
        }
        return false;
    }
}
