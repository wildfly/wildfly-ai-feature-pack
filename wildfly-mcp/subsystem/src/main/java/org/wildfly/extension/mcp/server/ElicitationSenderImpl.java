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
import org.wildfly.extension.mcp.api.InitializeRequest;
import org.wildfly.extension.mcp.api.Messages;
import org.wildfly.extension.mcp.api.Responder;
import org.wildfly.mcp.model.elicitation.Elicitation;
import org.wildfly.mcp.model.elicitation.ElicitationSender;
import org.wildfly.mcp.model.elicitation.ElicitationProperty;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;
import static org.wildfly.mcp.model.elicitation.Elicitation.Mode.FORM;
import static org.wildfly.mcp.model.elicitation.Elicitation.Mode.URL;

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
 *   <li>The result is parsed into an {@link Elicitation.Response} and returned to the tool.</li>
 * </ol>
 * </p>
 */
class ElicitationSenderImpl implements ElicitationSender {

    static final String ELICITATION_CREATE = "elicitation/create";
    static final String NOTIFICATIONS_ELICITATION_COMPLETE = "notifications/elicitation/complete";

    private final PendingRequestRegistry registry;
    private final Responder responder;
    private final InitializeRequest initializeRequest;

    ElicitationSenderImpl(PendingRequestRegistry registry, Responder responder, InitializeRequest initializeRequest) {
        this.registry = registry;
        this.responder = responder;
        this.initializeRequest = initializeRequest;
    }

    @Override
    public boolean isFormSupported() {
        return initializeRequest != null && initializeRequest.supportsElicitationForm();
    }

    @Override
    public boolean isUrlSupported() {
        return initializeRequest != null && initializeRequest.supportsElicitationUrl();
    }

    @Override
    public Elicitation.Response send(Elicitation request) throws Exception {
        return switch (request.mode()) {
            case FORM -> sendForm(request);
            case URL -> sendUrl(request);
        };
    }

    @Override
    public void notifyElicitationComplete(String elicitationId) {
        JsonObjectBuilder params = Json.createObjectBuilder()
                .add("elicitationId", elicitationId);
        responder.send(Messages.newNotification(NOTIFICATIONS_ELICITATION_COMPLETE, params));
        ROOT_LOGGER.debugf("Elicitation complete notification sent [elicitationId: %s]", elicitationId);
    }

    private Elicitation.Response sendForm(Elicitation request) throws Exception {
        if (!isFormSupported()) {
            throw ROOT_LOGGER.elicitationModeNotSupported(FORM);
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        long requestId = registry.register(future);

        JsonObjectBuilder properties = Json.createObjectBuilder();
        JsonArrayBuilder required = Json.createArrayBuilder();
        for (ElicitationProperty<?> property : request.schemaProperties()) {
            String key = property.name();
            properties.add(key, property.jsonSchema());
            if (property.required()) {
                required.add(key);
            }
        }
        JsonObjectBuilder schema = Json.createObjectBuilder()
                .add("type", "object")
                .add("properties", properties)
                .add("required", required);

        JsonObjectBuilder params = Json.createObjectBuilder()
                .add("mode", "form")
                .add("message", request.message())
                .add("requestedSchema", schema);

        responder.send(Messages.newRequest(requestId, ELICITATION_CREATE, params));
        ROOT_LOGGER.debugf("Elicitation request sent [id: %d, message: %s]", requestId, request.message());

        JsonObject responseMessage = awaitResponse(future, request.timeoutMillis(), requestId);
        return parseFormResponse(responseMessage);
    }

    private Elicitation.Response sendUrl(Elicitation request) throws Exception {
        if (!isUrlSupported()) {
            throw ROOT_LOGGER.elicitationModeNotSupported(URL);
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        long requestId = registry.register(future);

        JsonObjectBuilder params = Json.createObjectBuilder()
                .add("mode", "url")
                .add("message", request.message())
                .add("url", request.url())
                .add("elicitationId", request.elicitationId());

        responder.send(Messages.newRequest(requestId, ELICITATION_CREATE, params));
        ROOT_LOGGER.debugf("URL elicitation request sent [id: %d, url: %s, elicitationId: %s]",
                requestId, request.url(), request.elicitationId());

        JsonObject responseMessage = awaitResponse(future, request.timeoutMillis(), requestId);
        return parseUrlResponse(responseMessage);
    }

    private JsonObject awaitResponse(CompletableFuture<JsonObject> future, long timeoutMillis, long requestId)
            throws Exception {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            registry.remove(requestId);
            ROOT_LOGGER.elicitationTimedOut(requestId, timeoutMillis);
            throw te;
        }
    }

    private Elicitation.Response parseUrlResponse(JsonObject responseMessage) {
        JsonObject result = parseResult(responseMessage);
        Elicitation.Response.Action action = parseAction(result, responseMessage);
        return new Elicitation.Response(action, Map.of());
    }

    private Elicitation.Response parseFormResponse(JsonObject responseMessage) {
        JsonObject result = parseResult(responseMessage);
        Elicitation.Response.Action action = parseAction(result, responseMessage);
        return new Elicitation.Response(action, parseContent(result.getJsonObject("content")));
    }

    private static JsonObject parseResult(JsonObject responseMessage) {
        JsonObject result = responseMessage.getJsonObject("result");
        if (result == null) {
            throw new IllegalStateException("Invalid elicitation response (no result): " + responseMessage);
        }
        return result;
    }

    private static Elicitation.Response.Action parseAction(JsonObject result, JsonObject responseMessage) {
        String actionStr = result.getString("action", null);
        if (actionStr == null) {
            throw new IllegalStateException("Invalid elicitation response (no action): " + responseMessage);
        }
        return Elicitation.Response.Action.valueOf(actionStr.toUpperCase());
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
