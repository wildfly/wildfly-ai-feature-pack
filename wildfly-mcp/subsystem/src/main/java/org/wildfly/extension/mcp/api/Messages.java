/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class Messages {

    public static JsonObject newResult(String id, JsonObjectBuilder result) {
        JsonObjectBuilder response = Json.createObjectBuilder();
        response.add("jsonrpc", JsonRPC.VERSION);
        addId(response, id);
        response.add("result", result);
        return response.build();
    }

    public static JsonObject newError(String id, int code, String message) {
        String msg = message == null ? "" : message;
        JsonObjectBuilder response = Json.createObjectBuilder();
        response.add("jsonrpc", JsonRPC.VERSION);
        addId(response, id);
        response.add("error",
                    Json.createObjectBuilder()
                            .add("code", code)
                            .add("message", msg));
        return response.build();
    }

    /**
     * Adds the JSON-RPC {@code id} field to the response builder.
     * <p>
     * The id string is the {@link jakarta.json.JsonValue#toString()} representation: a bare number
     * for JSON numbers, a double-quoted string for JSON strings, or {@code null} for absent ids.
     * This method re-parses accordingly so the output preserves the original type.
     * </p>
     * <p>
     * <b>Limitation:</b> Only integer ({@code long}) and string ids are fully supported. Float ids
     * such as {@code 1.5} fail {@code Long.parseLong} and are not enclosed in double-quotes, so they
     * fall through to the else branch and are written back as JSON strings, silently changing their
     * type. The MCP spec uses integer and string ids in practice, so this is an accepted limitation.
     * </p>
     */
    private static void addId(JsonObjectBuilder response, String id) {
        if (id == null) {
            response.addNull("id");
            return;
        }
        try {
            response.add("id", Long.parseLong(id));
        } catch (NumberFormatException e) {
            // id is a JSON-encoded string: strip surrounding double quotes
            if (id.length() >= 2 && id.charAt(0) == '"' && id.charAt(id.length() - 1) == '"') {
                response.add("id", id.substring(1, id.length() - 1));
            } else {
                response.add("id", id);
            }
        }
    }

    /**
     * Creates a JSON-RPC notification message with no parameters.
     *
     * @param method the notification method name
     * @return a JSON-RPC notification object
     */
    public static JsonObject newNotification(String method) {
        return Json.createObjectBuilder()
                .add("jsonrpc", JsonRPC.VERSION)
                .add("method", method)
                .build();
    }

    public static JsonObject newNotification(String method, JsonObjectBuilder params) {
        return Json.createObjectBuilder()
                .add("jsonrpc", JsonRPC.VERSION)
                .add("method", method)
                .add("params", params)
                .build();
    }

    public static JsonObject newRequest(long id, String method, JsonObjectBuilder params) {
        return Json.createObjectBuilder()
                .add("jsonrpc", JsonRPC.VERSION)
                .add("id", id)
                .add("method", method)
                .add("params", params)
                .build();
    }

    public static boolean isResponse(JsonObject message) {
        return message.containsKey("result") || message.containsKey("error");
    }

}
