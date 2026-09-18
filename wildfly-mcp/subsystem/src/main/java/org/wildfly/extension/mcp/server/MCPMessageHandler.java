/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.MCPMethods.*;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.META;
import static org.wildfly.extension.mcp.injection.MCPFieldNames.REQUEST_STATE;
import static org.wildfly.extension.mcp.server.MCPServerUtils.asJsonObject;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.Implementation;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.MCPMessageListener;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.ProtocolVersion;
import org.wildfly.extension.mcp.api.RequestMetadata;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;


public class MCPMessageHandler {

    private final ConnectionManager connectionManager;
    private final WildFlyMCPRegistry registry;
    private final Map<String, Object> serverInfo;
    private final Map<String, Object> serverInfoDetails;
    private final Map<String, Object> serverCapabilities;
    private final ToolMessageHandler toolHandler;
    private final PromptMessageHandler promptHandler;
    private final ResourceMessageHandler resourceHandler;
    private final ResourceTemplateMessageHandler resourceTemplateHandler;
    private final CompletionHandler completionHandler;
    private final SubscriptionManager subscriptionManager;
    private final Map<String, SubscriptionStream> activeSubscriptionStreams = new java.util.concurrent.ConcurrentHashMap<>();
    private final RequestStateCodec requestStateCodec;
    private final List<MCPMessageListener> listeners;

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion) {
        this(connectionManager, registry, classLoader, serverName, serverVersion, MCPHandlerConfig.DEFAULT);
    }

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion, MCPHandlerConfig config) {
        this.registry = registry;
        ExecutorService executorService = lookupExecutorService();
        RequestStateCodec requestStateCodec = null;
        String requestStateSecret = config.requestStateSecret();
        if (requestStateSecret != null && !requestStateSecret.isEmpty()) {
            byte[] secretBytes;
            try {
                secretBytes = Base64.getDecoder().decode(requestStateSecret);
            } catch (IllegalArgumentException e) {
                throw ROOT_LOGGER.invalidRequestStateSecret(e);
            }
            requestStateCodec = new RequestStateCodec(secretBytes);
        }
        this.requestStateCodec = requestStateCodec;
        int pageSize = config.pageSize();
        this.connectionManager = connectionManager;
        this.subscriptionManager = new SubscriptionManager();
        this.toolHandler = new ToolMessageHandler(registry, classLoader, executorService, pageSize, requestStateCodec);
        this.toolHandler.setListChangeNotifier(new org.wildfly.mcp.api.ListChangeNotifier() {
            @Override public void notifyToolsChanged() { notifySubscriptionStreams("toolsListChanged"); }
            @Override public void notifyPromptsChanged() { notifySubscriptionStreams("promptsListChanged"); }
            @Override public void notifyResourcesChanged() { notifySubscriptionStreams("resourcesListChanged"); }
        });
        this.promptHandler = new PromptMessageHandler(registry, classLoader, executorService, pageSize, requestStateCodec);
        this.resourceHandler = new ResourceMessageHandler(registry, classLoader, executorService, pageSize, this.subscriptionManager, connectionManager);
        this.resourceHandler.setStreamNotifier(this::notifyResourceUpdatedOnStreams);
        this.resourceTemplateHandler = new ResourceTemplateMessageHandler(registry, classLoader, executorService, pageSize);
        this.completionHandler = new CompletionHandler(registry, classLoader);
        this.serverInfo = new HashMap<>();
        Map<String, Object> info = new HashMap<>();
        info.put(FIELD_NAME, serverName);
        info.put(FIELD_VERSION, serverVersion);
        this.serverInfoDetails = info;
        this.serverInfo.put("serverInfo", info);
        this.serverInfo.put(FIELD_PROTOCOL_VERSION, PROTOCOL_VERSION);
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("prompts", Map.of("listChanged", true));
        capabilities.put("tools", Map.of("listChanged", true));
        capabilities.put("resources", Map.of("subscribe", true, "listChanged", true));
        capabilities.put("completions", Map.of());
        this.serverCapabilities = capabilities;
        this.serverInfo.put(FIELD_CAPABILITIES, capabilities);
        this.cacheTtlMs = config.cacheTtlMs();
        this.cacheScope = config.cacheScope();
        this.listeners = config.listeners();
    }

    public void handle(JsonObject message, MCPConnection connection, Responder responder) {
        handle(message, connection, responder, null, -1, null, null);
    }

    public void handle(JsonObject message, MCPConnection connection, Responder responder,
                       String clientAddress, int clientPort, String networkProtocolVersion) {
        handle(message, connection, responder, clientAddress, clientPort, networkProtocolVersion, null);
    }

    public void handle(JsonObject message, MCPConnection connection, Responder responder,
                       String clientAddress, int clientPort, String networkProtocolVersion,
                       Map<String, String> mcpHeaders) {
        // Route client responses (e.g. elicitation/create replies) to any waiting future
        if (Messages.isResponse(message)) {
            connection.pendingRequests().handleResponse(message.get("id"), message);
            return;
        }

        String method = message.containsKey(FIELD_METHOD) ? message.getString(FIELD_METHOD) : "";
        String id = extractId(message);

        MCPMessageContextImpl context = new MCPMessageContextImpl(
                method, connection.id(), id, connection.status(), System.nanoTime());
        if (clientAddress != null) {
            context.setClientAddress(clientAddress);
            context.setClientPort(clientPort);
        }
        context.setNetworkProtocolVersion(networkProtocolVersion);
        context.setMcpHeaders(mcpHeaders);
        enrichContext(context, message, connection);
        fireBeforeMessage(context);
        if (context.hasError()) {
            boolean isNotification = id == null;
            if (!isNotification) {
                if (context.errorCode() == UNSUPPORTED_PROTOCOL_VERSION && context.requestedVersion() != null) {
                    sendUnsupportedVersionError(id, context.requestedVersion(), context.errorMessage(), responder);
                } else {
                    responder.sendError(id, context.errorCode(), context.errorMessage());
                }
            }
            fireError(context, null);
            return;
        }
        try {
            Responder effectiveResponder = responder;
            if (context.requestMetadata() != null) {
                effectiveResponder = new VersionAwareResponder(responder, context.requestMetadata().protocolVersion(), cacheTtlMs, cacheScope);
            }
            if (connection instanceof StatelessConnection) {
                operation(message, effectiveResponder, connection, context);
            } else switch (connection.status()) {
                case NEW ->
                    initializeNew(message, effectiveResponder, connection, context);
                case INITIALIZING ->
                    initializing(message, effectiveResponder, connection, context);
                case IN_OPERATION ->
                    operation(message, effectiveResponder, connection, context);
                case SHUTDOWN -> {
                    String shutdownMsg = ROOT_LOGGER.connectionAlreadyShutdown();
                    context.setErrorCode(JsonRPC.INTERNAL_ERROR);
                    context.setErrorMessage(shutdownMsg);
                    effectiveResponder.send(Messages.newError(id, JsonRPC.INTERNAL_ERROR, shutdownMsg));
                }
            }
            context.setDurationNanos(System.nanoTime() - context.startTimeNanos());
            // Always call fireError when an error code is set
            if (context.hasError()) {
                fireError(context, null);
            } else {
                fireAfterMessage(context);
            }
        } catch (Exception e) {
            context.setDurationNanos(System.nanoTime() - context.startTimeNanos());
            // Set error code if not already set by the handler
            if (!context.hasError()) {
                context.setErrorCode(JsonRPC.INTERNAL_ERROR);
                context.setErrorMessage(e.getMessage());
            }
            fireError(context, e);
            throw e;
        }
    }

    private void enrichContext(MCPMessageContextImpl context, JsonObject message, MCPConnection connection) {
        JsonObject params = asJsonObject(message.get(FIELD_PARAMS));
        extractRequestMetadata(context, params, connection);
        extractW3CTraceContext(context, params);
        decodeRequestState(context, params);
        extractOperationAttributes(context, params, connection);
    }

    private void extractRequestMetadata(MCPMessageContextImpl context, JsonObject params, MCPConnection connection) {
        if (params != null) {
            try {
                RequestMetadata metadata = RequestMetadata.from(params);
                if (metadata != null) {
                    context.setRequestMetadata(metadata);
                    context.setProtocolVersion(metadata.protocolVersion().wireValue());
                }
            } catch (RequestMetadata.UnsupportedProtocolVersionException e) {
                ROOT_LOGGER.debugf("Unsupported protocol version in request: %s", e.getMessage());
                context.setErrorCode(UNSUPPORTED_PROTOCOL_VERSION);
                context.setErrorMessage(e.getMessage());
                context.setRequestedVersion(e.requestedVersion());
            } catch (RequestMetadata.MCPMetadataValidationException e) {
                ROOT_LOGGER.debugf("Invalid _meta in request: %s", e.getMessage());
                context.setErrorCode(JsonRPC.INVALID_PARAMS);
                context.setErrorMessage(e.getMessage());
            }
        }
        if (context.requestMetadata() == null && connection instanceof StatelessConnection sc) {
            RequestMetadata connMetadata = sc.requestMetadata();
            if (connMetadata != null) {
                context.setRequestMetadata(connMetadata);
                context.setProtocolVersion(connMetadata.protocolVersion().wireValue());
            }
        }
    }

    private void extractW3CTraceContext(MCPMessageContextImpl context, JsonObject params) {
        if (params == null) {
            return;
        }
        Map<String, JsonValue> metaExtras = null;
        RequestMetadata rm = context.requestMetadata();
        if (rm != null) {
            metaExtras = rm.extra();
        } else {
            JsonObject meta = params.getJsonObject(META);
            if (meta != null) {
                metaExtras = new HashMap<>();
                for (String key : meta.keySet()) {
                    metaExtras.put(key, meta.get(key));
                }
            }
        }
        if (metaExtras != null) {
            Map<String, String> headers = new HashMap<>();
            JsonValue tp = metaExtras.get(W3C_TRACEPARENT);
            if (tp != null && tp.getValueType() == JsonValue.ValueType.STRING) {
                headers.put(W3C_TRACEPARENT, ((JsonString) tp).getString());
            }
            JsonValue ts = metaExtras.get(W3C_TRACESTATE);
            if (ts != null && ts.getValueType() == JsonValue.ValueType.STRING) {
                headers.put(W3C_TRACESTATE, ((JsonString) ts).getString());
            }
            if (!headers.isEmpty()) {
                context.setPropagationHeaders(headers);
            }
        }
    }

    private void decodeRequestState(MCPMessageContextImpl context, JsonObject params) {
        if (params == null || requestStateCodec == null
                || (!TOOLS_CALL.equals(context.method()) && !PROMPTS_GET.equals(context.method()))) {
            return;
        }
        JsonValue rsValue = params.get(REQUEST_STATE);
        if (rsValue != null && rsValue.getValueType() == JsonValue.ValueType.STRING) {
            String token = ((JsonString) rsValue).getString();
            try {
                RequestStateCodec.DecodedState decoded = requestStateCodec.decode(token);
                context.setDecodedRequestState(decoded);
            } catch (RequestStateCodec.RequestStateException e) {
                ROOT_LOGGER.debugf("requestState validation failed: %s", e.getMessage());
                context.setErrorCode(JsonRPC.INVALID_PARAMS);
                context.setErrorMessage("Invalid requestState: " + e.getMessage());
            }
        }
    }

    private void extractOperationAttributes(MCPMessageContextImpl context, JsonObject params, MCPConnection connection) {
        if (params != null && (TOOLS_CALL.equals(context.method()) || PROMPTS_GET.equals(context.method()))) {
            String name = params.getString(FIELD_NAME, null);
            if (name != null) {
                context.setGenAiTarget(name);
            }
        }
        InitializeRequest initReq = connection.initializeRequest();
        if (initReq != null) {
            context.setProtocolVersion(initReq.protocolVersion());
        }
        if (params != null) {
            String method = context.method();
            if (RESOURCES_READ.equals(method) || RESOURCES_SUBSCRIBE.equals(method) || RESOURCES_UNSUBSCRIBE.equals(method)) {
                String uri = params.getString(FIELD_URI, null);
                if (uri != null && !uri.isEmpty()) {
                    context.setResourceUri(uri);
                }
            }
        }
    }

    private void fireBeforeMessage(MCPMessageContextImpl context) {
        fireEvent(l -> l.onBeforeMessageDispatched(context), "onBeforeMessageDispatched");
    }

    private void fireAfterMessage(MCPMessageContextImpl context) {
        fireEvent(l -> l.onAfterMessageDispatched(context), "onAfterMessageDispatched");
    }

    private void fireError(MCPMessageContextImpl context, Throwable error) {
        fireEvent(l -> l.onError(context, error), "onError");
    }

    // Only Exception is caught: JVM errors (e.g. OutOfMemoryError) propagate out and abort
    // dispatch for remaining listeners. This is intentional — do not swallow critical errors.
    private void fireEvent(Consumer<MCPMessageListener> action, String callbackName) {
        for (MCPMessageListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                ROOT_LOGGER.debugf(e, "MCPMessageListener.%s failed", callbackName);
            }
        }
    }

    private void initializeNew(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        String id = context.requestId();
        String method = message.getString(FIELD_METHOD);
        if (SERVER_DISCOVER.equals(method)) {
            discover(message, responder);
            return;
        }
        // The first message must be "initialize"
        if (!INITIALIZE.equals(method)) {
            String msg = "The first message from the client must be \"initialize\": " + method;
            context.setErrorCode(JsonRPC.METHOD_NOT_FOUND);
            context.setErrorMessage(msg);
            responder.sendError(id, JsonRPC.METHOD_NOT_FOUND, msg);
            return;
        }
        JsonObject params = message.getJsonObject(FIELD_PARAMS);
        if (params == null) {
            String msg = "Initialization params not found";
            context.setErrorCode(JsonRPC.INVALID_PARAMS);
            context.setErrorMessage(msg);
            responder.sendError(id, JsonRPC.INVALID_PARAMS, msg);
            return;
        }
        InitializeRequest initRequest = decodeInitializeRequest(params);
        String clientVersion = initRequest.protocolVersion();
        java.util.Optional<ProtocolVersion> negotiated = ProtocolVersion.negotiate(clientVersion);
        if (negotiated.isEmpty()) {
            String errorMsg = "Unsupported protocol version: " + clientVersion;
            context.setErrorCode(UNSUPPORTED_PROTOCOL_VERSION);
            context.setErrorMessage(errorMsg);
            sendUnsupportedVersionError(id, clientVersion, errorMsg, responder);
            return;
        }
        InitializeRequest negotiatedRequest = new InitializeRequest(
                initRequest.implementation(), negotiated.get().wireValue(), initRequest.clientCapabilities());
        if (connection.initialize(negotiatedRequest)) {
            Map<String, Object> response = new java.util.HashMap<>(serverInfo);
            response.put(FIELD_PROTOCOL_VERSION, negotiated.get().wireValue());
            responder.sendResult(id, JsonRPC.convertMap(response));
        } else {
            String msg = "Unable to initialize connection [connectionId: " + connection.id() + "]";
            context.setErrorCode(JsonRPC.INTERNAL_ERROR);
            context.setErrorMessage(msg);
            responder.sendError(id, JsonRPC.INTERNAL_ERROR, msg);
        }
    }


    private void initializing(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        String method = message.getString(FIELD_METHOD);
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (connection.setInitialized()) {
                ROOT_LOGGER.debugf("Client successfully initialized [%s]", connection.id());
            }
        } else if (PING.equals(method)) {
            ping(message, responder);
        } else if (SERVER_DISCOVER.equals(method)) {
            discover(message, responder);
        } else {
            String msg = "Client not initialized yet [" + connection.id() + "]";
            context.setErrorCode(JsonRPC.INTERNAL_ERROR);
            context.setErrorMessage(msg);
            responder.send(Messages.newError(context.requestId(), JsonRPC.INTERNAL_ERROR, msg));
        }
    }

    // JSON-RPC and MCP message field names
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_ID = "id";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_PROTOCOL_VERSION = "protocolVersion";
    private static final String FIELD_CLIENT_INFO = "clientInfo";
    private static final String FIELD_CAPABILITIES = "capabilities";
    // W3C Trace Context header names propagated via params._meta
    private static final String W3C_TRACEPARENT = "traceparent";
    private static final String W3C_TRACESTATE = "tracestate";

    private static String extractId(JsonObject message) {
        if (!message.containsKey(FIELD_ID)) {
            return null;
        }
        JsonValue idValue = message.get(FIELD_ID);
        if (idValue == null || idValue.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        return idValue.toString();
    }

    private final long cacheTtlMs;
    private final String cacheScope;

    private static final Set<String> REMOVED_IN_2026_07_28 = Set.of(
            INITIALIZE, NOTIFICATIONS_INITIALIZED, PING,
            LOGGING_SET_LEVEL, NOTIFICATIONS_ROOTS_LIST_CHANGED,
            RESOURCES_SUBSCRIBE, RESOURCES_UNSUBSCRIBE);

    private void operation(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        String method = context.method();
        String id = context.requestId();
        RequestMetadata metadata = context.requestMetadata();
        if (metadata != null && metadata.protocolVersion() == ProtocolVersion.V_2026_07_28
                && REMOVED_IN_2026_07_28.contains(method)) {
            String msg = "Method '" + method + "' is removed in protocol version 2026-07-28";
            context.setErrorCode(JsonRPC.METHOD_NOT_FOUND);
            context.setErrorMessage(msg);
            boolean isNotification = !message.containsKey(FIELD_ID);
            if (!isNotification) {
                responder.sendError(id, JsonRPC.METHOD_NOT_FOUND, msg);
            }
            return;
        }
        switch (method) {
            case PROMPTS_LIST -> promptHandler.promptsList(message, responder);
            case PROMPTS_GET -> promptHandler.promptsGet(message, responder, connection, context.decodedRequestState());
            case TOOLS_LIST -> toolHandler.toolsList(message, responder);
            case TOOLS_CALL -> toolHandler.toolsCall(message, responder, connection, context.decodedRequestState(), context.mcpHeaders(), context.requestMetadata());
            case NOTIFICATIONS_CANCEL -> connection.cancel();
            case PING -> ping(message, responder);
            case LOGGING_SET_LEVEL -> loggingSetLevel(message, responder);
            case RESOURCES_LIST -> resourceHandler.resourcesList(message, responder);
            case RESOURCES_SUBSCRIBE -> resourceHandler.resourcesSubscribe(message, responder, connection);
            case RESOURCES_UNSUBSCRIBE -> resourceHandler.resourcesUnsubscribe(message, responder, connection);
            case RESOURCES_READ -> {
                JsonObject resourceParams = message.getJsonObject(FIELD_PARAMS);
                String resourceUri = resourceParams != null ? resourceParams.getString(FIELD_URI, "") : "";
                if (registry.getResource(resourceUri) != null) {
                    resourceHandler.resourceCall(message, responder, connection);
                } else {
                    resourceTemplateHandler.resourceTemplateRead(message, responder, connection);
                }
            }
            case RESOURCE_TEMPLATES_LIST ->
                resourceTemplateHandler.resourceTemplatesList(message, responder);
            case COMPLETION_COMPLETE ->
                complete(message, responder, connection);
            case SERVER_DISCOVER -> discover(message, responder);
            case SUBSCRIPTIONS_LISTEN -> subscriptionsListen(message, responder, connection, context);
            case Q_CLOSE -> close(message, responder, connection, context);
            default -> {
                String unsupportedMsg = ROOT_LOGGER.unsupportedMethod(method);
                context.setErrorCode(JsonRPC.METHOD_NOT_FOUND);
                context.setErrorMessage(unsupportedMsg);
                responder.send(Messages.newError(id, JsonRPC.METHOD_NOT_FOUND, unsupportedMsg));
            }
        }
    }

    private void complete(JsonObject message, Responder responder, MCPConnection connection) {
        completionHandler.complete(message, responder, connection);
    }

    private void discover(JsonObject message, Responder responder) {
        String id = extractId(message);
        JsonObjectBuilder result = Json.createObjectBuilder();
        JsonArrayBuilder versions = Json.createArrayBuilder();
        for (String v : ProtocolVersion.SUPPORTED_VERSIONS) {
            versions.add(v);
        }
        result.add("supportedVersions", versions);
        result.add(FIELD_CAPABILITIES, JsonRPC.convertMap(serverCapabilities));
        result.add("serverInfo", JsonRPC.convertMap(serverInfoDetails));
        result.add("extensions", Json.createObjectBuilder());
        result.add(META, Json.createObjectBuilder()
                .add("ttlMs", cacheTtlMs)
                .add("cacheScope", cacheScope)
                .add(RequestMetadata.MCP_SERVER_INFO,
                        Json.createObjectBuilder()
                                .add("name", serverInfoDetails.get("name").toString())
                                .add("version", serverInfoDetails.get("version").toString())));
        responder.sendResult(id, result);
    }

    private void subscriptionsListen(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        String id = context.requestId();
        JsonObject params = message.getJsonObject(FIELD_PARAMS);
        if (params == null) {
            context.setErrorCode(JsonRPC.INVALID_PARAMS);
            context.setErrorMessage("Missing params");
            responder.sendError(id, JsonRPC.INVALID_PARAMS, "Missing params");
            return;
        }

        Set<SubscriptionManager.SubscriptionTarget> targets = new HashSet<>();

        if (params.containsKey("notifications")) {
            JsonObject notifications = asJsonObject(params.get("notifications"));
            if (notifications == null) {
                context.setErrorCode(JsonRPC.INVALID_PARAMS);
                context.setErrorMessage("'notifications' must be an object");
                responder.sendError(id, JsonRPC.INVALID_PARAMS, "'notifications' must be an object");
                return;
            }
            if (notifications.getBoolean("resourcesListChanged", false)) {
                targets.add(new SubscriptionManager.SubscriptionTarget("resourcesListChanged", ""));
            }
            if (notifications.getBoolean("toolsListChanged", false)) {
                targets.add(new SubscriptionManager.SubscriptionTarget("toolsListChanged", ""));
            }
            if (notifications.getBoolean("promptsListChanged", false)) {
                targets.add(new SubscriptionManager.SubscriptionTarget("promptsListChanged", ""));
            }
            if (notifications.containsKey("resourceSubscriptions")) {
                JsonValue rsValue = notifications.get("resourceSubscriptions");
                if (rsValue != null && rsValue.getValueType() == JsonValue.ValueType.ARRAY) {
                    JsonArray uris = rsValue.asJsonArray();
                    for (int i = 0; i < uris.size(); i++) {
                        targets.add(new SubscriptionManager.SubscriptionTarget("resource", uris.getString(i)));
                    }
                }
            }
        } else if (params.containsKey("subscriptions")) {
            JsonValue subsValue = params.get("subscriptions");
            if (subsValue == null || subsValue.getValueType() != JsonValue.ValueType.ARRAY) {
                context.setErrorCode(JsonRPC.INVALID_PARAMS);
                context.setErrorMessage("'subscriptions' must be an array");
                responder.sendError(id, JsonRPC.INVALID_PARAMS, "'subscriptions' must be an array");
                return;
            }
            JsonArray subs = subsValue.asJsonArray();
            for (int i = 0; i < subs.size(); i++) {
                JsonValue element = subs.get(i);
                if (element.getValueType() != JsonValue.ValueType.OBJECT) {
                    context.setErrorCode(JsonRPC.INVALID_PARAMS);
                    context.setErrorMessage("Each subscription must be an object");
                    responder.sendError(id, JsonRPC.INVALID_PARAMS, "Each subscription must be an object");
                    return;
                }
                JsonObject sub = element.asJsonObject();
                String type = sub.getString("type", "");
                String uri = sub.getString(FIELD_URI, "");
                targets.add(new SubscriptionManager.SubscriptionTarget(type, uri));
            }
        } else {
            context.setErrorCode(JsonRPC.INVALID_PARAMS);
            context.setErrorMessage("Missing 'notifications' or 'subscriptions' field");
            responder.sendError(id, JsonRPC.INVALID_PARAMS, "Missing 'notifications' or 'subscriptions' field");
            return;
        }

        String subscriptionId = UUID.randomUUID().toString();
        String connectionId = null;
        if (!(connection instanceof StatelessConnection)) {
            subscriptionManager.listen(connection.id(), targets);
            connectionId = connection.id();
        }
        registerSubscriptionStream(subscriptionId, responder, targets, connectionId);
        ROOT_LOGGER.debugf("subscriptions/listen [id: %s, subscriptionId: %s, count: %d]", id, subscriptionId, targets.size());

        responder.send(Messages.newNotification(
                NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED,
                Json.createObjectBuilder()
                        .add(META, Json.createObjectBuilder()
                                .add(RequestMetadata.MCP_SUBSCRIPTION_ID, subscriptionId))));

        responder.sendResult(id, Json.createObjectBuilder()
                .add("subscriptionId", subscriptionId));
    }

    private void ping(JsonObject message, Responder responder) {
        // https://spec.modelcontextprotocol.io/specification/basic/utilities/ping/
        String id = message.get(FIELD_ID).toString();
        ROOT_LOGGER.debugf("Ping [id: %s]", id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    private void loggingSetLevel(JsonObject message, Responder responder) {
        String id = message.containsKey(FIELD_ID) && message.get(FIELD_ID) != null
                ? message.get(FIELD_ID).toString() : null;
        ROOT_LOGGER.debugf("logging/setLevel [id: %s]", id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    void cleanupConnection(MCPConnection connection) {
        resourceHandler.removeConnection(connection);
        subscriptionManager.removeConnection(connection.id());
        String connId = connection.id();
        activeSubscriptionStreams.entrySet().removeIf(e -> connId.equals(e.getValue().connectionId()));
    }

    private void close(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        cleanupConnection(connection);
        if (connectionManager.remove(connection.id())) {
            ROOT_LOGGER.debugf("Connection %s closed", connection.id());
        } else {
            String closeErrorMsg = ROOT_LOGGER.unableToObtainConnectionToClose(connection.id());
            context.setErrorCode(JsonRPC.INTERNAL_ERROR);
            context.setErrorMessage(closeErrorMsg);
            responder.sendError(context.requestId(), JsonRPC.INTERNAL_ERROR, closeErrorMsg);
        }
    }

    static ExecutorService lookupExecutorService() {
        InitialContext context = null;
        try {
            context = new InitialContext();
            return (ExecutorService) context.lookup("java:jboss/ee/concurrency/executor/default");
        } catch (NamingException ex) {
            ROOT_LOGGER.managedExecutorServiceNotAvailable();
            return Executors.newCachedThreadPool();
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ex) {
                    ROOT_LOGGER.debug("Error closing initial context", ex);
                }
            }
        }
    }

    private void sendUnsupportedVersionError(String id, String requestedVersion, String errorMessage, Responder responder) {
        JsonArrayBuilder supported = Json.createArrayBuilder();
        for (String v : ProtocolVersion.SUPPORTED_VERSIONS) {
            supported.add(v);
        }
        JsonObjectBuilder data = Json.createObjectBuilder()
                .add("supported", supported)
                .add("requested", requestedVersion != null ? requestedVersion : "null");
        responder.send(Messages.newErrorWithData(id, UNSUPPORTED_PROTOCOL_VERSION, errorMessage, data));
    }

    private InitializeRequest decodeInitializeRequest(JsonObject params) {
        JsonObject clientInfo = params.getJsonObject(FIELD_CLIENT_INFO);
        Implementation implementation;
        if (clientInfo != null) {
            String name = clientInfo.containsKey(FIELD_NAME) ? clientInfo.getString(FIELD_NAME) : "unknown";
            String version = clientInfo.containsKey(FIELD_VERSION) ? clientInfo.getString(FIELD_VERSION) : "unknown";
            implementation = new Implementation(name, version);
        } else {
            implementation = new Implementation("unknown", "unknown");
        }
        String protocolVersion = params.containsKey(FIELD_PROTOCOL_VERSION) ? params.getString(FIELD_PROTOCOL_VERSION) : PROTOCOL_VERSION;
        List<ClientCapability> clientCapabilities = RequestMetadata.parseClientCapabilities(
                params.get(FIELD_CAPABILITIES));
        return new InitializeRequest(implementation, protocolVersion, clientCapabilities);
    }

    SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    record SubscriptionStream(Responder responder, Set<SubscriptionManager.SubscriptionTarget> targets, String subscriptionId, String connectionId) {}

    void registerSubscriptionStream(String subscriptionId, Responder responder, Set<SubscriptionManager.SubscriptionTarget> targets, String connectionId) {
        if (connectionId != null) {
            activeSubscriptionStreams.entrySet().removeIf(e -> connectionId.equals(e.getValue().connectionId()));
        }
        activeSubscriptionStreams.put(subscriptionId, new SubscriptionStream(responder, Set.copyOf(targets), subscriptionId, connectionId));
    }

    void removeSubscriptionStream(String subscriptionId) {
        activeSubscriptionStreams.remove(subscriptionId);
    }

    void notifySubscriptionStreams(String targetType) {
        String notificationMethod = switch (targetType) {
            case "toolsListChanged" -> NOTIFICATIONS_TOOLS_LIST_CHANGED;
            case "promptsListChanged" -> NOTIFICATIONS_PROMPTS_LIST_CHANGED;
            case "resourcesListChanged" -> NOTIFICATIONS_RESOURCES_LIST_CHANGED;
            default -> null;
        };
        if (notificationMethod == null) {
            return;
        }
        SubscriptionManager.SubscriptionTarget target = new SubscriptionManager.SubscriptionTarget(targetType, "");
        for (SubscriptionStream stream : activeSubscriptionStreams.values()) {
            if (stream.targets().contains(target)) {
                JsonObject notification = Messages.newNotification(notificationMethod,
                        Json.createObjectBuilder()
                                .add(META, Json.createObjectBuilder()
                                        .add(RequestMetadata.MCP_SUBSCRIPTION_ID, stream.subscriptionId())));
                try {
                    stream.responder().send(notification);
                } catch (Exception e) {
                    ROOT_LOGGER.debugf("Failed to send %s to subscription stream %s", targetType, stream.subscriptionId());
                }
            }
        }
    }

    void notifyResourceUpdatedOnStreams(String uri, Set<String> alreadyNotifiedConnections) {
        SubscriptionManager.SubscriptionTarget target = new SubscriptionManager.SubscriptionTarget("resource", uri);
        for (SubscriptionStream stream : activeSubscriptionStreams.values()) {
            if (stream.targets().contains(target)) {
                if (stream.connectionId() != null && alreadyNotifiedConnections.contains(stream.connectionId())) {
                    continue;
                }
                JsonObject notification = Messages.newNotification("notifications/resources/updated",
                        Json.createObjectBuilder()
                                .add("uri", uri)
                                .add(META, Json.createObjectBuilder()
                                        .add(RequestMetadata.MCP_SUBSCRIPTION_ID, stream.subscriptionId())));
                try {
                    stream.responder().send(notification);
                } catch (Exception e) {
                    ROOT_LOGGER.debugf("Failed to send resource updated to subscription stream %s", stream.subscriptionId());
                }
            }
        }
    }

    Map<String, String> getToolHeaderMappings(String toolName) {
        MCPFeatureMetadata metadata = registry.getTool(toolName);
        if (metadata == null) {
            return Map.of();
        }
        Map<String, String> mappings = new HashMap<>();
        for (ArgumentMetadata arg : metadata.arguments()) {
            if (arg.isHeader()) {
                mappings.put(arg.headerName(), arg.headerName());
            }
        }
        return mappings;
    }
}
