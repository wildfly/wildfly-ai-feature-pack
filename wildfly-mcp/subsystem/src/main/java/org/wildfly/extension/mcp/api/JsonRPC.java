/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.wildfly.extension.mcp.api.Messages.isResponse;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class JsonRPC {

    public static final String VERSION = "2.0";

    public static final int INTERNAL_ERROR = -32603;
    public static final int INVALID_PARAMS = -32602;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_REQUEST = -32600;

    public static boolean validate(JsonObject message, Responder responder) {
        String id = message.get("id") == null ? null : message.get("id").toString();
        String jsonrpc = message.containsKey("jsonrpc") ? message.getString("jsonrpc") : null;
        if (!VERSION.equals(jsonrpc)) {
            responder.sendError(id, INVALID_REQUEST, "Invalid jsonrpc version: " + jsonrpc);
            return false;
        }
        if (!isResponse(message)) {
            if (message.get("method") == null) {
                responder.sendError(id, METHOD_NOT_FOUND, "Method not set");
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static JsonObjectBuilder convertMap(Map<String, Object> map) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, Object> info : map.entrySet()) {
            Object value = info.getValue();
            if (value instanceof BigDecimal bigDecimal) {
                builder.add(info.getKey(), bigDecimal);
            } else if (value instanceof BigInteger bigInteger) {
                builder.add(info.getKey(), bigInteger);
            } else if (value instanceof JsonValue jsonValue) {
                builder.add(info.getKey(), jsonValue);
            } else if (value instanceof String string) {
                builder.add(info.getKey(), string);
            } else if (value instanceof Integer integer) {
                builder.add(info.getKey(), integer);
            } else if (value instanceof Long aLong) {
                builder.add(info.getKey(), aLong);
            } else if (value instanceof Double aDouble) {
                builder.add(info.getKey(), aDouble);
            } else if (value instanceof Boolean aBoolean) {
                builder.add(info.getKey(), aBoolean);
            } else if (value instanceof Map map1) {
                builder.add(info.getKey(), convertMap(map1));
            } else {
                builder.addNull(info.getKey());
            }
        }
        return builder;
    }
}
