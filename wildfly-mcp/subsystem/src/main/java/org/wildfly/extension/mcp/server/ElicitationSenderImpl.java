/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.wildfly.extension.mcp.MCPLogger;
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationRequest;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationResponse;
import org.wildfly.extension.mcp.injection.elicitation.ElicitationSender;
import org.wildfly.extension.mcp.injection.elicitation.PrimitiveSchema;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

/**
 * Subsystem-side implementation of {@link ElicitationSender}.
 *
 * <p>When {@link #send} is called from a tool thread:
 * <ol>
 *   <li>A {@link CompletableFuture} is registered in the connection's {@link PendingRequestRegistry}.</li>
 *   <li>An {@code elicitation/create} JSON-RPC request is sent to the client via the {@link Responder}.</li>
 *   <li>The tool thread blocks on {@code future.get(timeout)} until the client responds.</li>
 *   <li>When the client response arrives, {@link MCPMessageHandler} routes it to
 *       {@link PendingRequestRegistry#handleResponse}, completing the future.</li>
 *   <li>The result is parsed into an {@link ElicitationResponse} and returned to the tool.</li>
 * </ol>
 * </p>
 */
class ElicitationSenderImpl implements ElicitationSender {

    static final String ELICITATION_CREATE = "elicitation/create";

    private final PendingRequestRegistry registry;
    private final Responder responder;
    private final InitializeRequest initializeRequest;

    ElicitationSenderImpl(PendingRequestRegistry registry, Responder responder, InitializeRequest initializeRequest) {
        this.registry = registry;
        this.responder = responder;
        this.initializeRequest = initializeRequest;
    }

    @Override
    public boolean isSupported() {
        return initializeRequest != null && initializeRequest.supportsElicitation();
    }

    @Override
    public ElicitationResponse send(ElicitationRequest request) throws Exception {
        if (!isSupported()) {
            throw new IllegalStateException("Client does not support the 'elicitation' capability");
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        long requestId = registry.register(future);

        // Build requestedSchema
        JsonObjectBuilder properties = Json.createObjectBuilder();
        JsonArrayBuilder required = Json.createArrayBuilder();
        for (Map.Entry<String, PrimitiveSchema> entry : request.schemaProperties().entrySet()) {
            properties.add(entry.getKey(), entry.getValue().asJson());
            if (entry.getValue().required()) {
                required.add(entry.getKey());
            }
        }
        JsonObjectBuilder schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", properties)
                .add("required", required);

        JsonObjectBuilder params = Json.createObjectBuilder()
                .add("message", request.message())
                .add("requestedSchema", schema);

        responder.send(Messages.newRequest(requestId, ELICITATION_CREATE, params));
        ROOT_LOGGER.debugf("Elicitation request sent [id: %d, message: %s]", requestId, request.message());

        // Block the tool thread until the client responds or the timeout expires
        JsonObject responseMessage;
        try {
            responseMessage = future.get(request.timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            registry.remove(requestId);
            ROOT_LOGGER.elicitationTimedOut(requestId, request.timeoutMillis());
            throw te;
        }

        return parseResponse(responseMessage);
    }

    private ElicitationResponse parseResponse(JsonObject responseMessage) {
        JsonObject result = responseMessage.getJsonObject("result");
        if (result == null) {
            throw new IllegalStateException("Invalid elicitation response (no result): " + responseMessage);
        }
        String actionStr = result.getString("action", null);
        if (actionStr == null) {
            throw new IllegalStateException("Invalid elicitation response (no action): " + responseMessage);
        }
        ElicitationResponse.Action action = ElicitationResponse.Action.valueOf(actionStr.toUpperCase());

        JsonObject contentJson = result.getJsonObject("content");
        Map<String, Object> content = parseContent(contentJson);

        return new ElicitationResponse(action, content);
    }

    private Map<String, Object> parseContent(JsonObject contentJson) {
        if (contentJson == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : contentJson.keySet()) {
            JsonValue val = contentJson.get(key);
            switch (val.getValueType()) {
                case STRING -> map.put(key, contentJson.getString(key));
                case TRUE -> map.put(key, Boolean.TRUE);
                case FALSE -> map.put(key, Boolean.FALSE);
                case NUMBER -> {
                    JsonNumber n = contentJson.getJsonNumber(key);
                    map.put(key, n.isIntegral() ? n.intValue() : n.doubleValue());
                }
                case ARRAY -> {
                    JsonArray arr = contentJson.getJsonArray(key);
                    List<String> strings = new ArrayList<>(arr.size());
                    for (int i = 0; i < arr.size(); i++) {
                        strings.add(arr.getString(i));
                    }
                    map.put(key, strings);
                }
                default -> {
                    // NULL or nested OBJECT — skip
                }
            }
        }
        return map;
    }
}
