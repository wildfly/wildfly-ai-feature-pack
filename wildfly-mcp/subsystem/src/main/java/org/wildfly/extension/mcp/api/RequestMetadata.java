/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wildfly.extension.mcp.injection.MCPFieldNames;

public record RequestMetadata(
        ProtocolVersion protocolVersion,
        List<ClientCapability> clientCapabilities,
        Map<String, JsonValue> extra) {

    private static final String META = MCPFieldNames.META;
    public static final String MCP_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String MCP_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    public static final String MCP_SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    public static final String MCP_SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";

    /**
     * Extracts per-request metadata from the {@code _meta} field of a JSON-RPC params object.
     *
     * @param params the JSON-RPC {@code params} object, may be {@code null}
     * @return a {@code RequestMetadata} if the MCP namespace keys are present, or {@code null}
     *         if the request is from a legacy client (no MCP namespace keys in _meta)
     * @throws MCPMetadataValidationException if one MCP namespace key is present but the other is missing
     */
    public static RequestMetadata from(JsonObject params) {
        if (params == null) {
            return null;
        }
        JsonValue metaValue = params.get(META);
        if (metaValue == null || metaValue.getValueType() != JsonValue.ValueType.OBJECT) {
            return null;
        }
        JsonObject meta = metaValue.asJsonObject();

        boolean hasVersion = meta.containsKey(MCP_PROTOCOL_VERSION);
        boolean hasCapabilities = meta.containsKey(MCP_CLIENT_CAPABILITIES);

        if (!hasVersion && !hasCapabilities) {
            return null;
        }

        if (hasVersion && !hasCapabilities) {
            throw ROOT_LOGGER.missingMetadataField(MCP_CLIENT_CAPABILITIES);
        }
        if (!hasVersion && hasCapabilities) {
            throw ROOT_LOGGER.missingMetadataField(MCP_PROTOCOL_VERSION);
        }

        String versionStr = meta.getString(MCP_PROTOCOL_VERSION);
        ProtocolVersion version = ProtocolVersion.from(versionStr)
                .orElseThrow(() -> new UnsupportedProtocolVersionException(versionStr));

        List<ClientCapability> capabilities = parseClientCapabilities(meta.get(MCP_CLIENT_CAPABILITIES));

        Map<String, JsonValue> extra = new LinkedHashMap<>();
        for (String key : meta.keySet()) {
            if (!MCP_PROTOCOL_VERSION.equals(key) && !MCP_CLIENT_CAPABILITIES.equals(key)) {
                extra.put(key, meta.get(key));
            }
        }

        return new RequestMetadata(version, capabilities, extra);
    }

    public boolean hasCapability(String capabilityName) {
        if (clientCapabilities == null) {
            return false;
        }
        return clientCapabilities.stream().anyMatch(c -> capabilityName.equals(c.name()));
    }

    public static List<ClientCapability> parseClientCapabilities(JsonValue value) {
        List<ClientCapability> result = new ArrayList<>();
        if (value == null || value.getValueType() != JsonValue.ValueType.OBJECT) {
            return result;
        }
        JsonObject capsObj = value.asJsonObject();
        for (String name : capsObj.keySet()) {
            Set<String> capabilities = new LinkedHashSet<>();
            JsonValue capValue = capsObj.get(name);
            if (capValue.getValueType() == JsonValue.ValueType.OBJECT) {
                capabilities.addAll(capValue.asJsonObject().keySet());
            }
            result.add(new ClientCapability(name, capabilities));
        }
        return result;
    }

    public static class MCPMetadataValidationException extends RuntimeException {
        public MCPMetadataValidationException(String message) {
            super(message);
        }
    }

    public static class UnsupportedProtocolVersionException extends MCPMetadataValidationException {
        private final String requestedVersion;

        public UnsupportedProtocolVersionException(String requestedVersion) {
            super("Unsupported protocol version: " + requestedVersion);
            this.requestedVersion = requestedVersion;
        }

        public String requestedVersion() {
            return requestedVersion;
        }
    }
}
