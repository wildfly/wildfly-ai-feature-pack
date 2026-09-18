/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.wildfly.extension.mcp.api.ProtocolVersion;
import org.wildfly.extension.mcp.api.Responder;

/**
 * Decorator over {@link Responder} that adjusts response structure based on protocol version.
 * <p>
 * For {@code V_2026_07_28} clients, adds {@code resultType: "complete"} to all results
 * that don't already carry a {@code resultType}. For legacy clients, passes responses through unchanged.
 * </p>
 */
class VersionAwareResponder implements Responder {

    private final Responder delegate;
    private final ProtocolVersion version;
    private final long cacheTtlMs;
    private final String cacheScope;

    VersionAwareResponder(Responder delegate, ProtocolVersion version, long cacheTtlMs, String cacheScope) {
        this.delegate = delegate;
        this.version = version;
        this.cacheTtlMs = cacheTtlMs;
        this.cacheScope = cacheScope;
    }

    @Override
    public int lastEventId() {
        return delegate.lastEventId();
    }

    @Override
    public void send(JsonObject message) {
        delegate.send(adaptResponse(message));
    }

    @Override
    public void sendSync(JsonObject message) throws InterruptedException {
        delegate.sendSync(adaptResponse(message));
    }

    private JsonObject adaptResponse(JsonObject message) {
        if (!message.containsKey("result")) {
            return message;
        }
        if (version == ProtocolVersion.V_2025_03_26 || version == ProtocolVersion.V_2025_11_25) {
            return message;
        }
        return addModernFields(message);
    }

    private JsonObject addModernFields(JsonObject message) {
        JsonObject result = message.getJsonObject("result");
        boolean modified = false;
        JsonObjectBuilder newResult = Json.createObjectBuilder(result);
        if (!result.containsKey("resultType")) {
            newResult.add("resultType", "complete");
            modified = true;
        }
        if (isCacheableResult(result)) {
            if (!result.containsKey("cacheScope")) {
                newResult.add("cacheScope", cacheScope);
                modified = true;
            }
            if (!result.containsKey("ttlMs")) {
                newResult.add("ttlMs", cacheTtlMs);
                modified = true;
            }
        }
        if (modified) {
            return Json.createObjectBuilder(message)
                    .add("result", newResult)
                    .build();
        }
        return message;
    }

    private static boolean isCacheableResult(JsonObject result) {
        return result.containsKey("tools")
                || result.containsKey("resources")
                || result.containsKey("prompts")
                || result.containsKey("resourceTemplates")
                || result.containsKey("contents")
                || result.containsKey("supportedVersions");
    }
}
