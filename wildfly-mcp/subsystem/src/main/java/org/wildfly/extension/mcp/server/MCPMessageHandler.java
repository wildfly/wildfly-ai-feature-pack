/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

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
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.Implementation;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

public class MCPMessageHandler {

    private final ConnectionManager connectionManager;
    private final WildFlyMCPRegistry registry;
    private final Map<String, Object> serverInfo;
    private final ToolMessageHandler toolHandler;
    private final PromptMessageHandler promptHandler;
    private final ResourceMessageHandler resourceHandler;
    private final ResourceTemplateMessageHandler resourceTemplateHandler;
    private final CompletionHandler completionHandler;

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion) {
        this(connectionManager, registry, classLoader, serverName, serverVersion, 0);
    }

    public MCPMessageHandler(ConnectionManager connectionManager, WildFlyMCPRegistry registry, ClassLoader classLoader, String serverName, String serverVersion, int pageSize) {
        this.registry = registry;
        ExecutorService executorService = lookupExecutorService();
        this.toolHandler = new ToolMessageHandler(registry, classLoader, executorService, pageSize);
        this.promptHandler = new PromptMessageHandler(registry, classLoader, executorService, pageSize);
        this.resourceHandler = new ResourceMessageHandler(registry, classLoader, executorService, pageSize);
        this.resourceTemplateHandler = new ResourceTemplateMessageHandler(registry, classLoader, executorService, pageSize);
        this.completionHandler = new CompletionHandler(registry, classLoader);
        this.connectionManager = connectionManager;
        this.serverInfo = new HashMap<>();
        this.serverInfo.put("serverInfo", Map.of("name", serverName, "version", serverVersion));
        this.serverInfo.put("protocolVersion", PROTOCOL_VERSION);
        Map<String, Map<String, Object>> capabilities = new HashMap<>();
        capabilities.put("prompts", Map.of("listChanged", true));
        capabilities.put("tools", Map.of("listChanged", true));
        capabilities.put("resources", Map.of("subscribe", true, "listChanged", true));
        capabilities.put("completions", Map.of());
        this.serverInfo.put("capabilities", capabilities);
    }

    public void handle(JsonObject message, MCPConnection connection, Responder responder) {
        if (Messages.isResponse(message)) {
            // Route client responses (e.g. elicitation/create replies) to any waiting future
            connection.pendingRequests().handleResponse(message.get("id"), message);
        } else {
            switch (connection.status()) {
                case NEW ->
                    initializeNew(message, responder, connection);
                case INITIALIZING ->
                    initializing(message, responder, connection);
                case IN_OPERATION ->
                    operation(message, responder, connection);
                case SHUTDOWN ->
                    responder.send(
                            Messages.newError(message.get("id").toString(), JsonRPC.INTERNAL_ERROR, "Connection was already shut down"));
            }
        }
    }

    private void initializeNew(JsonObject message, Responder responder, MCPConnection connection) {
        String id = message.get("id").toString();
        // The first message must be "initialize"
        String method = message.getString("method");
        if (!INITIALIZE.equals(method)) {
            responder.sendError(id, JsonRPC.METHOD_NOT_FOUND,
                    "The first message from the client must be \"initialize\": " + method);
            return;
        }
        JsonObject params = message.getJsonObject("params");
        if (params == null) {
            responder.sendError(id, JsonRPC.INVALID_PARAMS, "Initialization params not found");
            return;
        }
        // TODO schema validation?
        if (connection.initialize(decodeInitializeRequest(params))) {
            // The server MUST respond with its own capabilities and information
            responder.sendResult(id, JsonRPC.convertMap(serverInfo));
        } else {
            responder.sendError(id, JsonRPC.INTERNAL_ERROR,
                    "Unable to initialize connection [connectionId: " + connection.id() + "]");
        }
    }


    private void initializing(JsonObject message, Responder responder, MCPConnection connection) {
        String method = message.getString("method");
        if (NOTIFICATIONS_INITIALIZED.equals(method)) {
            if (connection.setInitialized()) {
                ROOT_LOGGER.debugf("Client successfully initialized [%s]", connection.id());
            }
        } else if (PING.equals(method)) {
            ping(message, responder);
        } else {
            responder.send(Messages.newError(message.get("id").toString(), JsonRPC.INTERNAL_ERROR,
                    "Client not initialized yet [" + connection.id() + "]"));
        }
    }

    static final String PROTOCOL_VERSION = "2025-03-26";
    static final String INITIALIZE = "initialize";
    static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
    static final String NOTIFICATIONS_MESSAGE = "notifications/message";
    static final String NOTIFICATIONS_CANCEL = "notifications/cancelled";
    static final String PROMPTS_LIST = "prompts/list";
    static final String PROMPTS_GET = "prompts/get";
    static final String TOOLS_LIST = "tools/list";
    static final String TOOLS_CALL = "tools/call";
    static final String RESOURCES_LIST = "resources/list";
    static final String RESOURCE_TEMPLATES_LIST = "resources/templates/list";
    static final String RESOURCES_READ = "resources/read";
    static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
    static final String RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
    static final String PING = "ping";
    static final String COMPLETION_COMPLETE = "completion/complete";
    static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
    // non-standard messages
    static final String Q_CLOSE = "q/close";

    private void operation(JsonObject message, Responder responder, MCPConnection connection) {
        String method = message.get("method") == null ? "" : message.getString("method");
        String id = message.get("id") != null ? message.get("id").toString() : "";
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
                String resourceUri = message.getJsonObject("params") != null ? message.getJsonObject("params").getString("uri", "") : "";
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
            case Q_CLOSE -> close(message, responder, connection);
            default ->
                responder.send(
                        Messages.newError(id, JsonRPC.METHOD_NOT_FOUND, "Unsupported method: " + method));
        }
    }

    private void complete(JsonObject message, Responder responder, MCPConnection connection) {
        completionHandler.complete(message, responder, connection);
    }

    private void ping(JsonObject message, Responder responder) {
        // https://spec.modelcontextprotocol.io/specification/basic/utilities/ping/
        String id = message.get("id").toString();
        ROOT_LOGGER.debugf("Ping [id: %s]", id);
        responder.sendResult(id, Json.createObjectBuilder());
    }

    private void close(JsonObject message, Responder responder, MCPConnection connection) {
        resourceHandler.removeConnection(connection);
        if (connectionManager.remove(connection.id())) {
            ROOT_LOGGER.debugf("Connection %s closed", connection.id());
        } else {
            responder.sendError(message.get("id").toString(), JsonRPC.INTERNAL_ERROR,
                    "Unable to obtain the connection to be closed:" + connection.id());
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
        JsonObject clientInfo = params.getJsonObject("clientInfo");
        Implementation implementation = new Implementation(clientInfo.getString("name"), clientInfo.getString("version"));
        String protocolVersion = params.getString("protocolVersion");
        List<ClientCapability> clientCapabilities = new ArrayList<>();
        JsonObject capabilities = params.getJsonObject("capabilities");
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
