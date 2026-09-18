package org.wildfly.extension.mcp.server;

import static io.undertow.util.Headers.ALLOW;
import static io.undertow.util.Headers.CACHE_CONTROL;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.Headers.ORIGIN;
import static io.undertow.util.HttpString.tryFromString;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_METHOD_HEADER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_NAME_HEADER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_PROTOCOL_VERSION_HEADER;
import static org.wildfly.extension.mcp.api.ConnectionManager.MCP_SESSION_ID_HEADER;
import static org.wildfly.extension.mcp.api.MCPMethods.HEADER_MISMATCH;
import static org.wildfly.extension.mcp.api.MCPMethods.INITIALIZE;
import static org.wildfly.extension.mcp.api.MCPMethods.PROMPTS_GET;
import static org.wildfly.extension.mcp.api.MCPMethods.PROTOCOL_VERSION;
import static org.wildfly.extension.mcp.api.MCPMethods.TOOLS_CALL;
import static org.wildfly.extension.mcp.api.MCPMethods.RESOURCES_READ;
import static org.wildfly.extension.mcp.api.MCPMethods.UNSUPPORTED_PROTOCOL_VERSION;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.META;
import static org.wildfly.extension.mcp.server.MCPServerUtils.JSON_CONTENT_TYPE;
import static org.wildfly.extension.mcp.server.MCPServerUtils.asJsonObject;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.ProtocolVersion;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.RequestMetadata;
import org.wildfly.extension.mcp.api.Responder;

public class StreamableHttpHandler implements HttpHandler {

    private final ConnectionManager connectionManager;
    private final MCPMessageHandler handler;
    private final ServerSentEventHandler sseHandler;
    private final Set<String> allowedOrigins;

    public StreamableHttpHandler(ConnectionManager connectionManager, MCPMessageHandler handler,
            ServerSentEventHandler sseHandler) {
        this(connectionManager, handler, sseHandler, Collections.emptySet());
    }

    public StreamableHttpHandler(ConnectionManager connectionManager, MCPMessageHandler handler,
            ServerSentEventHandler sseHandler, Set<String> allowedOrigins) {
        this.connectionManager = connectionManager;
        this.handler = handler;
        this.sseHandler = sseHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // --- Origin validation (DNS rebinding protection) [Task 4] ---
        if (!MCPServerUtils.validateOrigin(exchange, allowedOrigins)) {
            return;
        }

        if (Methods.GET.equals(exchange.getRequestMethod())) {
            String sessionId = exchange.getRequestHeaders().getFirst(MCP_SESSION_ID_HEADER);
            if (sessionId != null) {
                MCPConnection existing = connectionManager.get(sessionId);
                if (existing == null) {
                    ROOT_LOGGER.unknownSession(sessionId);
                    sendJsonError(exchange, 404, null, JsonRPC.INVALID_REQUEST,
                            "Unknown session: " + sessionId);
                    return;
                }
                exchange.getResponseHeaders().put(MCP_SESSION_ID_HEADER, sessionId);
            }
            exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
            setCorsHeaders(exchange);
            exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
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
            exchange.setStatusCode(406);
            exchange.endExchange();
            return;
        }
        boolean jsonOnly = !acceptsSse(accepts);

        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        exchange.startBlocking();

        // --- JSON-RPC batch rejection [Task 7] ---
        JsonReader reader = Json.createReader(exchange.getInputStream());
        JsonValue parsed;
        try {
            parsed = reader.read();
        } catch (Exception e) {
            sendJsonError(exchange, 400, null, JsonRPC.INVALID_REQUEST, "Invalid JSON");
            return;
        }

        if (parsed.getValueType() == JsonValue.ValueType.ARRAY) {
            ROOT_LOGGER.batchRequestRejected();
            sendJsonError(exchange, 400, null, JsonRPC.INVALID_REQUEST,
                    "JSON-RPC batch requests are not supported; send one request per POST");
            return;
        }

        JsonObject content = asJsonObject(parsed);
        if (content == null) {
            sendJsonError(exchange, 400, null, JsonRPC.INVALID_REQUEST, "Expected a JSON object");
            return;
        }
        ROOT_LOGGER.debugf("Received message from client: %s", content);

        // --- Mcp-Method header validation [Task 3] ---
        if (!validateMcpMethodHeader(exchange, content)) {
            return;
        }

        // --- Mcp-Name header validation [Task 3] ---
        if (!validateMcpNameHeader(exchange, content)) {
            return;
        }

        // --- Mcp-Param-* header validation [Task 8] ---
        if (!validateMcpParamHeaders(exchange, content)) {
            return;
        }

        String connectionId = exchange.getRequestHeaders().getFirst(MCP_SESSION_ID_HEADER);
        boolean isNotification = !content.containsKey("id");
        if (connectionId == null) {
            // First message (no session yet)
            if (isNotification) {
                exchange.setStatusCode(202);
                exchange.endExchange();
                return;
            }
            String method = content.getString("method", "");
            String protocolVersionHeader = exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION_HEADER);
            if (jsonOnly || !INITIALIZE.equals(method) || ProtocolVersion.V_2026_07_28.is(protocolVersionHeader)) {
                handleStatelessRequest(exchange, content);
                return;
            }
            connectionId = connectionManager.id();
            InetSocketAddress src = exchange.getSourceAddress();
            connectionManager.setPending(connectionId, new ConnectionManager.PendingMessage(
                    content,
                    src != null ? src.getHostString() : null,
                    src != null ? src.getPort() : -1,
                    MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                    extractMcpHeaders(exchange),
                    System.currentTimeMillis()));
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(MCP_SESSION_ID_HEADER, connectionId);
            exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
            setCorsHeaders(exchange);
            exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
            final String pendingId = connectionId;
            exchange.addExchangeCompleteListener((ex, nextListener) -> {
                connectionManager.takePending(pendingId);
                nextListener.proceed();
            });
            this.sseHandler.handleRequest(exchange);
            return;
        }

        // Existing session — validate protocol version header
        String protocolVersion = exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION_HEADER);
        if (protocolVersion != null && ProtocolVersion.from(protocolVersion).isEmpty()) {
            ROOT_LOGGER.invalidProtocolVersion(PROTOCOL_VERSION, protocolVersion);
            String requestId = extractRequestId(content);
            JsonArrayBuilder supported = Json.createArrayBuilder();
            for (String v : ProtocolVersion.SUPPORTED_VERSIONS) {
                supported.add(v);
            }
            JsonObjectBuilder data = Json.createObjectBuilder()
                    .add("supported", supported)
                    .add("requested", protocolVersion);
            sendJsonError(exchange, 400, requestId, UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version: " + protocolVersion, data);
            return;
        }

        // --- 404 for unknown session [Task 5] ---
        MCPConnection connection = connectionManager.get(connectionId);
        if (connection == null) {
            ROOT_LOGGER.unknownSession(connectionId);
            String requestId = extractRequestId(content);
            sendJsonError(exchange, 404, requestId, JsonRPC.INVALID_REQUEST,
                    "Unknown session: " + connectionId);
            return;
        }

        // Reject protocol version mismatch against the session's negotiated version
        if (protocolVersion != null && connection.initializeRequest() != null) {
            String negotiated = connection.initializeRequest().protocolVersion();
            if (negotiated != null && !negotiated.equals(protocolVersion)) {
                String requestId = extractRequestId(content);
                sendJsonError(exchange, 400, requestId, UNSUPPORTED_PROTOCOL_VERSION,
                        "MCP-Protocol-Version header '" + protocolVersion
                                + "' does not match session's negotiated version '" + negotiated + "'");
                return;
            }
        }

        // --- 202 for notifications [Task 5] ---
        if (isNotification) {
            handler.handle(content, connection, NO_OP_RESPONDER,
                    getClientAddress(exchange), getClientPort(exchange),
                    MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                    extractMcpHeaders(exchange));
            exchange.setStatusCode(202);
            exchange.endExchange();
            return;
        }

        // --- 202 for client response envelopes (e.g. elicitation/create replies) ---
        if (Messages.isResponse(content)) {
            handler.handle(content, connection, NO_OP_RESPONDER,
                    getClientAddress(exchange), getClientPort(exchange),
                    MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                    extractMcpHeaders(exchange));
            exchange.setStatusCode(202);
            exchange.endExchange();
            return;
        }

        // Request with existing session — respond via per-POST SSE stream (SEP-1699)
        ServerSentEventResponder sseConnection = (ServerSentEventResponder) connection;
        JsonRPC.validate(content, sseConnection);

        exchange.setStatusCode(200);
        exchange.getResponseHeaders().put(MCP_SESSION_ID_HEADER, connectionId);
        exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
        exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
        setCorsHeaders(exchange);

        OutputStream os = exchange.getOutputStream();
        int primingId = sseConnection.lastEventId();
        os.write(("retry: 5000\nid: " + primingId + "\ndata:\n\n").getBytes(StandardCharsets.UTF_8));
        os.flush();

        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicBoolean exchangeDone = new AtomicBoolean(false);

        Responder postResponder = new Responder() {
            @Override
            public int lastEventId() {
                return sseConnection.lastEventId();
            }

            @Override
            public void send(JsonObject message) {
                if (exchangeDone.get()) {
                    return;
                }
                if (message.containsKey("method")) {
                    sseConnection.send(message);
                    return;
                }
                try {
                    int id = sseConnection.lastEventId();
                    byte[] event = ("event: message\nid: " + id + "\ndata: " + message.toString() + "\n\n")
                            .getBytes(StandardCharsets.UTF_8);
                    synchronized (os) {
                        os.write(event);
                        os.flush();
                    }
                } catch (IOException e) {
                    exchangeDone.set(true);
                }
                if (message.containsKey("result") || message.containsKey("error")) {
                    responseLatch.countDown();
                }
            }
        };

        handler.handle(content, sseConnection, postResponder,
                getClientAddress(exchange), getClientPort(exchange),
                MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                extractMcpHeaders(exchange));

        try {
            if (!responseLatch.await(30, TimeUnit.SECONDS)) {
                exchangeDone.set(true);
                sseConnection.cancel();
                try {
                    int id = sseConnection.lastEventId();
                    String errorJson = Messages.newError(extractRequestId(content),
                            JsonRPC.INTERNAL_ERROR, "Request timed out").toString();
                    byte[] event = ("event: message\nid: " + id + "\ndata: " + errorJson + "\n\n")
                            .getBytes(StandardCharsets.UTF_8);
                    synchronized (os) {
                        os.write(event);
                        os.flush();
                    }
                } catch (IOException e) {
                    // Client disconnected
                }
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchangeDone.set(true);
            sseConnection.cancel();
        }
    }

    // ---- Mcp-Method header validation [Task 3] ----

    private boolean validateMcpMethodHeader(HttpServerExchange exchange, JsonObject content) {
        String mcpMethod = exchange.getRequestHeaders().getFirst(MCP_METHOD_HEADER);
        String jsonRpcMethod = content.getString("method", "");
        if (mcpMethod == null) {
            String protocolVersion = exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION_HEADER);
            if (ProtocolVersion.V_2026_07_28.is(protocolVersion)) {
                String requestId = extractRequestId(content);
                sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                        "Missing Mcp-Method header; required for protocol version 2026-07-28");
                return false;
            }
            return true;
        }
        if (!mcpMethod.equals(jsonRpcMethod)) {
            ROOT_LOGGER.headerMismatch("Mcp-Method", mcpMethod, "method", jsonRpcMethod);
            String requestId = extractRequestId(content);
            sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                    "Mcp-Method header '" + mcpMethod + "' does not match JSON-RPC method '" + jsonRpcMethod + "'");
            return false;
        }
        return true;
    }

    // ---- Mcp-Name header validation [Task 3] ----

    private static final Set<String> NAME_REQUIRED_METHODS = Set.of(TOOLS_CALL, PROMPTS_GET);
    private static final Set<String> URI_NAME_METHODS = Set.of(RESOURCES_READ);

    private boolean validateMcpNameHeader(HttpServerExchange exchange, JsonObject content) {
        String mcpName = exchange.getRequestHeaders().getFirst(MCP_NAME_HEADER);
        String method = content.getString("method", "");
        JsonObject params = content.getJsonObject("params");

        boolean requiresName = NAME_REQUIRED_METHODS.contains(method) || URI_NAME_METHODS.contains(method);
        String bodyName;
        if (URI_NAME_METHODS.contains(method)) {
            bodyName = params != null ? params.getString("uri", "") : "";
        } else {
            bodyName = params != null ? params.getString("name", "") : "";
        }

        if (mcpName == null) {
            if (requiresName && !bodyName.isEmpty()) {
                String protocolVersion = exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION_HEADER);
                if (ProtocolVersion.V_2026_07_28.is(protocolVersion)) {
                    String requestId = extractRequestId(content);
                    sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                            "Missing Mcp-Name header; required for " + method + " when params contains a name");
                    return false;
                }
            }
            return true;
        }
        String trimmedName = mcpName.strip();
        if (!trimmedName.equals(bodyName)) {
            ROOT_LOGGER.headerMismatch("Mcp-Name", mcpName, "params.name", bodyName);
            String requestId = extractRequestId(content);
            sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                    "Mcp-Name header '" + mcpName + "' does not match JSON-RPC params.name '" + bodyName + "'");
            return false;
        }
        return true;
    }

    // ---- Mcp-Param-* header validation [Task 8] ----

    private static final String MCP_PARAM_PREFIX = "mcp-param-";

    private boolean validateMcpParamHeaders(HttpServerExchange exchange, JsonObject content) {
        JsonObject params = content.getJsonObject("params");
        if (params == null) {
            return true;
        }
        JsonObject arguments = params.getJsonObject("arguments");
        if (arguments == null) {
            return true;
        }
        String method = content.getString("method", "");
        Map<String, String> headerMappings = Map.of();
        if (TOOLS_CALL.equals(method) && params.containsKey("name")) {
            headerMappings = handler.getToolHeaderMappings(params.getString("name"));
        }
        Set<String> presentParamHeaders = new HashSet<>();
        for (var headerName : exchange.getRequestHeaders().getHeaderNames()) {
            String name = headerName.toString().toLowerCase();
            if (!name.startsWith(MCP_PARAM_PREFIX)) {
                continue;
            }
            String paramKey = name.substring(MCP_PARAM_PREFIX.length());
            presentParamHeaders.add(paramKey);
            String headerValue = exchange.getRequestHeaders().getFirst(headerName);
            String bodyKey = headerMappings.containsKey(paramKey) ? headerMappings.get(paramKey) : paramKey;
            if (!arguments.containsKey(bodyKey)) {
                for (String argKey : arguments.keySet()) {
                    if (argKey.equalsIgnoreCase(bodyKey)) {
                        bodyKey = argKey;
                        break;
                    }
                }
            }
            if (arguments.containsKey(bodyKey)) {
                String bodyValue = normalizeJsonValue(arguments.get(bodyKey));
                String decodedHeader;
                try {
                    decodedHeader = decodeHeaderValue(headerValue);
                } catch (IllegalArgumentException e) {
                    String requestId = extractRequestId(content);
                    sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                            "Mcp-Param-" + paramKey + " header contains invalid Base64 encoding");
                    return false;
                }
                if (!decodedHeader.equals(bodyValue)) {
                    ROOT_LOGGER.headerMismatch("Mcp-Param-" + paramKey, headerValue, "params.arguments." + bodyKey, bodyValue);
                    String requestId = extractRequestId(content);
                    sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                            "Mcp-Param-" + paramKey + " header '" + headerValue
                                    + "' does not match body parameter '" + bodyValue + "'");
                    return false;
                }
            }
        }
        for (Map.Entry<String, String> entry : headerMappings.entrySet()) {
            String headerKey = entry.getKey();
            String bodyKey = entry.getValue();
            if (arguments.containsKey(bodyKey) && !presentParamHeaders.contains(headerKey)) {
                String requestId = extractRequestId(content);
                sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                        "Missing Mcp-Param-" + headerKey + " header; required when params.arguments."
                                + bodyKey + " is present");
                return false;
            }
        }
        return true;
    }

    private static String decodeHeaderValue(String value) {
        if (value != null && value.startsWith("=?base64?") && value.endsWith("?=")) {
            String encoded = value.substring("=?base64?".length(), value.length() - "?=".length());
            if (encoded.length() % 4 != 0) {
                throw ROOT_LOGGER.invalidBase64Padding(encoded.length());
            }
            return new String(java.util.Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
        }
        return value;
    }

    private static String normalizeJsonValue(JsonValue value) {
        if (value == null) {
            return "";
        }
        return switch (value.getValueType()) {
            case STRING -> ((jakarta.json.JsonString) value).getString();
            case NUMBER -> value.toString();
            case TRUE -> "true";
            case FALSE -> "false";
            case NULL -> "";
            default -> value.toString();
        };
    }

    private static Map<String, String> extractMcpHeaders(HttpServerExchange exchange) {
        return MCPServerUtils.extractMcpHeaders(exchange);
    }

    // ---- Helpers ----

    private static String extractRequestId(JsonObject content) {
        return content.containsKey("id") ? content.get("id").toString() : null;
    }

    private static void setCorsHeaders(HttpServerExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst(ORIGIN);
        String allowOrigin = (origin != null && !origin.isEmpty()) ? origin : "*";
        exchange.getResponseHeaders().put(tryFromString("Access-Control-Allow-Origin"), allowOrigin);
        exchange.getResponseHeaders().put(tryFromString("Access-Control-Expose-Headers"), "mcp-session-id");
        if (origin != null && !origin.isEmpty()) {
            exchange.getResponseHeaders().add(Headers.VARY, "Origin");
        }
    }

    private static void sendJsonError(HttpServerExchange exchange, int httpStatus, String requestId, int rpcCode, String message) {
        exchange.setStatusCode(httpStatus);
        exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
        exchange.getResponseSender().send(Messages.newError(requestId, rpcCode, message).toString());
    }

    private static void sendJsonError(HttpServerExchange exchange, int httpStatus, String requestId,
            int rpcCode, String message, JsonObjectBuilder data) {
        exchange.setStatusCode(httpStatus);
        exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
        exchange.getResponseSender().send(Messages.newErrorWithData(requestId, rpcCode, message, data).toString());
    }

    private static String getClientAddress(HttpServerExchange exchange) {
        InetSocketAddress src = exchange.getSourceAddress();
        return src != null ? src.getHostString() : null;
    }

    private static int getClientPort(HttpServerExchange exchange) {
        InetSocketAddress src = exchange.getSourceAddress();
        return src != null ? src.getPort() : -1;
    }

    private static boolean isValidAcceptHeader(HeaderValues accepts) {
        if (accepts == null) {
            return false;
        }
        for (String accept : accepts) {
            if (accept.contains(JSON_CONTENT_TYPE) || accept.contains("text/event-stream")) {
                return true;
            }
        }
        return false;
    }

    private static boolean acceptsSse(HeaderValues accepts) {
        if (accepts == null) {
            return false;
        }
        for (String accept : accepts) {
            if (accept.contains("text/event-stream")) {
                return true;
            }
        }
        return false;
    }

    private void handleStatelessRequest(HttpServerExchange exchange, JsonObject content) {
        if (Messages.isResponse(content)) {
            sendJsonError(exchange, 400, extractRequestId(content), JsonRPC.INVALID_REQUEST,
                    "Response envelopes are not accepted on stateless endpoints");
            return;
        }

        String protocolVersionHeader = exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION_HEADER);
        boolean is2026 = ProtocolVersion.V_2026_07_28.is(protocolVersionHeader);
        String requestId = extractRequestId(content);

        JsonObject params = content.getJsonObject("params");

        if (is2026 && params != null) {
            JsonObject meta = params.getJsonObject(META);
            if (meta != null) {
                String bodyVersion = meta.getString(RequestMetadata.MCP_PROTOCOL_VERSION, null);
                if (bodyVersion != null && protocolVersionHeader != null
                        && !bodyVersion.equals(protocolVersionHeader)) {
                    sendJsonError(exchange, 400, requestId, HEADER_MISMATCH,
                            "MCP-Protocol-Version header '" + protocolVersionHeader
                                    + "' does not match _meta protocolVersion '" + bodyVersion + "'");
                    return;
                }
            }
        }

        RequestMetadata metadata = null;
        if (params != null) {
            try {
                metadata = RequestMetadata.from(params);
            } catch (RequestMetadata.UnsupportedProtocolVersionException e) {
                JsonArrayBuilder supported = Json.createArrayBuilder();
                for (String v : ProtocolVersion.SUPPORTED_VERSIONS) {
                    supported.add(v);
                }
                JsonObjectBuilder data = Json.createObjectBuilder()
                        .add("supported", supported)
                        .add("requested", e.requestedVersion());
                sendJsonError(exchange, 400, requestId, UNSUPPORTED_PROTOCOL_VERSION,
                        e.getMessage(), data);
                return;
            } catch (RequestMetadata.MCPMetadataValidationException e) {
                sendJsonError(exchange, 400, requestId, JsonRPC.INVALID_PARAMS, e.getMessage());
                return;
            }
        }

        if (is2026 && metadata == null) {
            sendJsonError(exchange, 400, requestId, JsonRPC.INVALID_PARAMS,
                    "Missing required _meta with '" + RequestMetadata.MCP_PROTOCOL_VERSION + "' and "
                            + "'" + RequestMetadata.MCP_CLIENT_CAPABILITIES + "' for protocol version 2026-07-28");
            return;
        }

        String method = content.getString("method", "");
        if ("subscriptions/listen".equals(method)) {
            handleStatelessSubscriptionsListen(exchange, content, metadata);
            return;
        }

        StatelessConnection connection = new StatelessConnection(metadata);
        java.util.List<JsonObject> responses = new java.util.ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean exchangeHandled = new java.util.concurrent.atomic.AtomicBoolean();

        Responder jsonResponder = new Responder() {
            @Override
            public int lastEventId() {
                return 0;
            }

            @Override
            public void send(JsonObject message) {
                if (exchangeHandled.get()) {
                    return;
                }
                synchronized (responses) {
                    responses.add(message);
                }
                if (message.containsKey("result") || message.containsKey("error")) {
                    latch.countDown();
                }
            }
        };

        handler.handle(content, connection, jsonResponder,
                getClientAddress(exchange), getClientPort(exchange),
                MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                extractMcpHeaders(exchange));

        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                exchangeHandled.set(true);
                connection.cancel();
                sendJsonError(exchange, 504, extractRequestId(content),
                        JsonRPC.INTERNAL_ERROR, "Request timed out");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchangeHandled.set(true);
            connection.cancel();
            sendJsonError(exchange, 500, extractRequestId(content),
                    JsonRPC.INTERNAL_ERROR, "Request processing interrupted");
            return;
        }

        synchronized (responses) {
            if (responses.isEmpty()) {
                sendJsonError(exchange, 500, extractRequestId(content),
                        JsonRPC.INTERNAL_ERROR, "No response generated");
            } else if (responses.size() == 1) {
                JsonObject response = responses.get(0);
                int httpStatus = 200;
                if (response.containsKey("error")) {
                    int errorCode = response.getJsonObject("error").getInt("code", 0);
                    httpStatus = mapJsonRpcErrorToHttpStatus(errorCode);
                }
                exchange.setStatusCode(httpStatus);
                exchange.getResponseHeaders().put(CONTENT_TYPE, JSON_CONTENT_TYPE);
                setCorsHeaders(exchange);
                exchange.getResponseSender().send(response.toString());
            } else {
                exchange.setStatusCode(200);
                exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
                exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
                setCorsHeaders(exchange);
                try {
                    java.io.OutputStream os = exchange.getOutputStream();
                    int eventId = 0;
                    for (JsonObject msg : responses) {
                        os.write(("event: message\nid: " + (eventId++) + "\ndata: " + msg.toString() + "\n\n")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    os.flush();
                } catch (java.io.IOException e) {
                    // Client disconnected
                }
            }
        }
    }

    private void handleStatelessSubscriptionsListen(HttpServerExchange exchange, JsonObject content, RequestMetadata metadata) {
        exchange.setStatusCode(200);
        exchange.getResponseHeaders().put(CONTENT_TYPE, "text/event-stream");
        exchange.getResponseHeaders().put(CACHE_CONTROL, "no-cache");
        setCorsHeaders(exchange);

        StatelessConnection connection = new StatelessConnection(metadata);
        java.util.concurrent.atomic.AtomicInteger eventCounter = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> subscriptionIdRef = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch resultLatch = new CountDownLatch(1);

        Responder sseResponder = new Responder() {
            @Override
            public int lastEventId() {
                return eventCounter.get();
            }

            @Override
            public void send(JsonObject message) {
                try {
                    java.io.OutputStream os = exchange.getOutputStream();
                    int id = eventCounter.getAndIncrement();
                    os.write(("event: message\nid: " + id + "\ndata: " + message.toString() + "\n\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    os.flush();
                } catch (java.io.IOException e) {
                    // Client disconnected
                }
                if (message.containsKey("result")) {
                    JsonObject result = message.getJsonObject("result");
                    if (result != null && result.containsKey("subscriptionId")) {
                        subscriptionIdRef.set(result.getString("subscriptionId"));
                    }
                    resultLatch.countDown();
                } else if (message.containsKey("error")) {
                    resultLatch.countDown();
                }
            }
        };

        handler.handle(content, connection, sseResponder,
                getClientAddress(exchange), getClientPort(exchange),
                MCPServerUtils.parseNetworkProtocolVersion(exchange.getProtocol()),
                extractMcpHeaders(exchange));

        try {
            if (!resultLatch.await(30, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        String subscriptionId = subscriptionIdRef.get();
        if (subscriptionId == null) {
            return;
        }

        CountDownLatch disconnectLatch = new CountDownLatch(1);
        exchange.addExchangeCompleteListener((ex, nextListener) -> {
            handler.removeSubscriptionStream(subscriptionId);
            disconnectLatch.countDown();
            nextListener.proceed();
        });

        exchange.dispatch(exchange.getConnection().getWorker(), () -> {
            try {
                disconnectLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static int mapJsonRpcErrorToHttpStatus(int jsonRpcCode) {
        return switch (jsonRpcCode) {
            case JsonRPC.METHOD_NOT_FOUND -> 404;
            default -> 400;
        };
    }

    private static final Responder NO_OP_RESPONDER = new Responder() {
        @Override
        public int lastEventId() {
            return 0;
        }

        @Override
        public void send(JsonObject message) {
            // no-op: notifications don't get responses
        }
    };
}
