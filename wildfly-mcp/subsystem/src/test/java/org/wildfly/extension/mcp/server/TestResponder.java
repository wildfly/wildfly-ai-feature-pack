/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.wildfly.extension.mcp.api.Responder;

public class TestResponder implements Responder {

    private final List<JsonObject> messages = new ArrayList<>();
    private int eventId = 0;

    @Override
    public int lastEventId() {
        return eventId++;
    }

    @Override
    public void send(JsonObject message) {
        messages.add(message);
    }

    public JsonObject lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public List<JsonObject> allMessages() {
        return messages;
    }

    public void clear() {
        messages.clear();
    }

    public boolean hasResult() {
        return messages.stream().anyMatch(m -> m.containsKey("result"));
    }

    public boolean hasError() {
        return messages.stream().anyMatch(m -> m.containsKey("error"));
    }

    public JsonObject lastResult() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonObject m = messages.get(i);
            if (m.containsKey("result")) {
                return m.getJsonObject("result");
            }
        }
        return null;
    }

    public JsonObject firstResult() {
        return messages.stream()
                .filter(m -> m.containsKey("result"))
                .findFirst()
                .map(m -> m.getJsonObject("result"))
                .orElse(null);
    }

    public JsonObject lastError() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonObject m = messages.get(i);
            if (m.containsKey("error")) {
                return m.getJsonObject("error");
            }
        }
        return null;
    }
}
