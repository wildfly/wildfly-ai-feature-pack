/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.tool;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returned by a tool to signal that additional client input is needed before
 * the tool can produce a final result (MRTR / SEP-2322).
 *
 * <p>The server serializes this as a {@code resultType: "input_required"} response
 * containing {@code inputRequests} and an optional {@code requestState}.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * return InputRequiredResult.builder()
 *     .addElicitation("confirm", "Please confirm",
 *         Json.createObjectBuilder()
 *             .add("type", "object")
 *             .add("properties", Json.createObjectBuilder()
 *                 .add("ok", Json.createObjectBuilder().add("type", "boolean")))
 *             .add("required", Json.createArrayBuilder().add("ok")))
 *     .requestState(opaqueState)
 *     .build();
 * }</pre>
 */
public final class InputRequiredResult {

    private final Map<String, JsonObject> inputRequests;
    private final String requestState;

    private InputRequiredResult(Map<String, JsonObject> inputRequests, String requestState) {
        this.inputRequests = Map.copyOf(inputRequests);
        this.requestState = requestState;
    }

    public Map<String, JsonObject> inputRequests() {
        return inputRequests;
    }

    public String requestState() {
        return requestState;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, JsonObject> inputRequests = new LinkedHashMap<>();
        private String requestState;

        private Builder() {
        }

        /**
         * Adds an {@code elicitation/create} input request.
         *
         * @param key unique key for this input request
         * @param message the message to show the user
         * @param requestedSchema the JSON Schema for the requested form data
         */
        public Builder addElicitation(String key, String message, JsonObjectBuilder requestedSchema) {
            inputRequests.put(key, Json.createObjectBuilder()
                    .add("method", "elicitation/create")
                    .add("params", Json.createObjectBuilder()
                            .add("message", message)
                            .add("requestedSchema", requestedSchema))
                    .build());
            return this;
        }

        /**
         * Adds a {@code sampling/createMessage} input request.
         *
         * @param key unique key for this input request
         * @param messages the sampling messages
         * @param maxTokens maximum tokens for the response
         */
        public Builder addSampling(String key, JsonArrayBuilder messages, int maxTokens) {
            inputRequests.put(key, Json.createObjectBuilder()
                    .add("method", "sampling/createMessage")
                    .add("params", Json.createObjectBuilder()
                            .add("messages", messages)
                            .add("maxTokens", maxTokens))
                    .build());
            return this;
        }

        /**
         * Adds a {@code roots/list} input request.
         *
         * @param key unique key for this input request
         */
        public Builder addRootsList(String key) {
            inputRequests.put(key, Json.createObjectBuilder()
                    .add("method", "roots/list")
                    .add("params", Json.createObjectBuilder())
                    .build());
            return this;
        }

        /**
         * Adds a raw input request with the given method and params.
         */
        public Builder addInputRequest(String key, String method, JsonObjectBuilder params) {
            inputRequests.put(key, Json.createObjectBuilder()
                    .add("method", method)
                    .add("params", params)
                    .build());
            return this;
        }

        /**
         * Sets the opaque server state to be echoed back by the client on the next round.
         */
        public Builder requestState(String requestState) {
            this.requestState = requestState;
            return this;
        }

        public InputRequiredResult build() {
            if (inputRequests.isEmpty()) {
                throw new IllegalStateException("InputRequiredResult must have at least one inputRequest");
            }
            return new InputRequiredResult(inputRequests, requestState);
        }
    }
}
