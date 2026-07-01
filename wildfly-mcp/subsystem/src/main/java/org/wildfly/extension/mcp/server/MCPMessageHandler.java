/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.extension.mcp.api.MCPMethods.*;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;


public class MCPMessageHandler {

    private final ConnectionManager connectionManager;
    private final WildFlyMCPRegistry registry;
    private final Map<String, Object> serverInfo;
    private final ToolMessageHandler toolHandler;
    private final PromptMessageHandler promptHandler;
    private final ResourceMessageHandler resourceHandler;
    private final ResourceTemplateMessageHandler resourceTemplateHandler;
    private final CompletionHandler completionHandler;
    private final List<MCPMessageListener> listeners;

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion) {
        this(connectionManager, registry, classLoader, serverName, serverVersion, 0, List.of());
    }

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion, int pageSize) {
        this(connectionManager, registry, classLoader, serverName, serverVersion, pageSize, List.of());
    }

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion, int pageSize, List<MCPMessageListener> listeners) {
        this.registry = registry;
        ExecutorService executorService = lookupExecutorService();
        this.toolHandler = new ToolMessageHandler(registry, classLoader, executorService, pageSize);
        this.promptHandler = new PromptMessageHandler(registry, classLoader, executorService, pageSize);
        this.resourceHandler = new ResourceMessageHandler(registry, classLoader, executorService, pageSize);
        this.resourceTemplateHandler = new ResourceTemplateMessageHandler(registry, classLoader, executorService, pageSize);
        this.completionHandler = new CompletionHandler(registry, classLoader);
        this.connectionManager = connectionManager;
        this.serverInfo = new HashMap<>();
        this.serverInfo.put("serverInfo", Map.of(FIELD_NAME, serverName, FIELD_VERSION, serverVersion));
        this.serverInfo.put(FIELD_PROTOCOL_VERSION, PROTOCOL_VERSION);
        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        capabilities.put("prompts", Map.of("listChanged", true));
        capabilities.put("tools", Map.of("listChanged", true));
        capabilities.put("resources", Map.of("subscribe", true, "listChanged", true));
        capabilities.put("completions", Map.of());
        this.serverInfo.put(FIELD_CAPABILITIES, capabilities);
        this.listeners = listeners != null ? List.copyOf(listeners) : List.of();
    }

    public void handle(JsonObject message, MCPConnection connection, Responder responder) {
        handle(message, connection, responder, null, -1, null);
    }

    public void handle(JsonObject message, MCPConnection connection, Responder responder,
                       String clientAddress, int clientPort, String networkProtocolVersion) {
        // Route client responses (e.g. elicitation/create replies) to any waiting future
        if (Messages.isResponse(message)) {
            connection.pendingRequests().handleResponse(message.get("id"), message);
            return;
        }

        String method = message.containsKey(FIELD_METHOD) ? message.getString(FIELD_METHOD) : "";
        String id = message.containsKey(FIELD_ID) && message.get(FIELD_ID) != null ? message.get(FIELD_ID).toString() : "";

        MCPMessageContextImpl context = new MCPMessageContextImpl(
                method, connection.id(), id, connection.status(), System.nanoTime());
        if (clientAddress != null) {
            context.setClientAddress(clientAddress);
            context.setClientPort(clientPort);
        }
        context.setNetworkProtocolVersion(networkProtocolVersion);
        enrichContext(context, message, connection);
        fireBeforeMessage(context);
        try {
            switch (connection.status()) {
                case NEW ->
                    initializeNew(message, responder, connection, context);
                case INITIALIZING ->
                    initializing(message, responder, connection, context);
                case IN_OPERATION ->
                    operation(message, responder, connection, context);
                case SHUTDOWN -> {
                    String shutdownMsg = ROOT_LOGGER.connectionAlreadyShutdown();
                    context.setErrorCode(JsonRPC.INTERNAL_ERROR);
                    context.setErrorMessage(shutdownMsg);
                    responder.send(Messages.newError(id, JsonRPC.INTERNAL_ERROR, shutdownMsg));
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
        JsonObject params = message.containsKey(FIELD_PARAMS) ? message.getJsonObject(FIELD_PARAMS) : null;

        // W3C trace context from params._meta — lets the listener use the client's trace as remote parent
        if (params != null) {
            JsonObject meta = params.getJsonObject(FIELD_META);
            if (meta != null) {
                Map<String, String> headers = new HashMap<>();
                if (meta.containsKey(W3C_TRACEPARENT)) {
                    headers.put(W3C_TRACEPARENT, meta.getString(W3C_TRACEPARENT));
                }
                if (meta.containsKey(W3C_TRACESTATE)) {
                    headers.put(W3C_TRACESTATE, meta.getString(W3C_TRACESTATE));
                }
                if (!headers.isEmpty()) {
                    context.setPropagationHeaders(headers);
                }
            }
        }

        // gen_ai target: tool name for tools/call, prompt name for prompts/get
        if (params != null && (TOOLS_CALL.equals(context.method()) || PROMPTS_GET.equals(context.method()))) {
            String name = params.getString(FIELD_NAME, null);
            if (name != null) {
                context.setGenAiTarget(name);
            }
        }

        // mcp.protocol.version from the InitializeRequest negotiated during handshake
        InitializeRequest initReq = connection.initializeRequest();
        if (initReq != null) {
            context.setProtocolVersion(initReq.protocolVersion());
        }

        // mcp.resource.uri — conditionally required for resource methods that include a URI param
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
        // The first message must be "initialize"
        String method = message.getString(FIELD_METHOD);
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
        // TODO schema validation?
        InitializeRequest initializeRequest = decodeInitializeRequest(params);
        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(initializeRequest.protocolVersion())) {
            ROOT_LOGGER.invalidProtocolVersion(PROTOCOL_VERSION, initializeRequest.protocolVersion());
            String msg = ROOT_LOGGER.unsupportedProtocolVersion(initializeRequest.protocolVersion(), SUPPORTED_PROTOCOL_VERSIONS);
            context.setErrorCode(JsonRPC.INVALID_PARAMS);
            context.setErrorMessage(msg);
            responder.sendError(id, JsonRPC.INVALID_PARAMS, msg);
            return;
        }
        if (connection.initialize(initializeRequest)) {
            // The server MUST respond with its own capabilities and information
            responder.sendResult(id, JsonRPC.convertMap(serverInfo));
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
    private static final String FIELD_META = "_meta";
    // W3C Trace Context header names propagated via params._meta
    private static final String W3C_TRACEPARENT = "traceparent";
    private static final String W3C_TRACESTATE = "tracestate";

    private void operation(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        String method = context.method();
        String id = context.requestId();
        switch (method) {
            case PROMPTS_LIST -> promptHandler.promptsList(message, responder);
            case PROMPTS_GET -> promptHandler.promptsGet(message, responder, connection);
            case TOOLS_LIST -> toolHandler.toolsList(message, responder);
            case TOOLS_CALL -> toolHandler.toolsCall(message, responder, connection);
            case NOTIFICATIONS_CANCEL -> connection.cancel();
            case PING -> ping(message, responder);
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

    private void ping(JsonObject message, Responder responder) {
        // https://spec.modelcontextprotocol.io/specification/basic/utilities/ping/
        String id = message.get(FIELD_ID).toString();
        ROOT_LOGGER.debugf("Ping [id: %s]", id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    private void close(JsonObject message, Responder responder, MCPConnection connection, MCPMessageContextImpl context) {
        resourceHandler.removeConnection(connection);
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

    private InitializeRequest decodeInitializeRequest(JsonObject params) {
        JsonObject clientInfo = params.getJsonObject(FIELD_CLIENT_INFO);
        Implementation implementation = new Implementation(clientInfo.getString(FIELD_NAME), clientInfo.getString(FIELD_VERSION));
        String protocolVersion = params.getString(FIELD_PROTOCOL_VERSION);
        List<ClientCapability> clientCapabilities = new ArrayList<>();
        JsonObject capabilities = params.getJsonObject(FIELD_CAPABILITIES);
        if (capabilities != null) {
            for (String name : capabilities.keySet()) {
                JsonValue capValue = capabilities.get(name);
                Map<String, Object> properties = new LinkedHashMap<>();
                if (capValue.getValueType() == JsonValue.ValueType.OBJECT) {
                    for (String prop : capValue.asJsonObject().keySet()) {
                        properties.put(prop, Map.of());
                    }
                }
                clientCapabilities.add(new ClientCapability(name, properties));
            }
        }
        return new InitializeRequest(implementation, protocolVersion, clientCapabilities);
    }
}
