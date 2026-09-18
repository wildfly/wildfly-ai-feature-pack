/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.Test;

public class RequestMetadataTestCase {

    @Test
    public void testParseValidMeta() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()
                                .add("elicitation", Json.createObjectBuilder()
                                        .add("form", Json.createObjectBuilder()))))
                .build();

        RequestMetadata metadata = RequestMetadata.from(params);

        assertNotNull(metadata);
        assertEquals(ProtocolVersion.V_2026_07_28, metadata.protocolVersion());
        assertNotNull(metadata.clientCapabilities());
        assertEquals(1, metadata.clientCapabilities().size());
        assertEquals("elicitation", metadata.clientCapabilities().get(0).name());
    }

    @Test
    public void testParseReturnsNullWhenParamsNull() {
        assertNull(RequestMetadata.from(null));
    }

    @Test
    public void testParseReturnsNullWhenMetaAbsent() {
        JsonObject params = Json.createObjectBuilder()
                .add("name", "echo")
                .build();
        assertNull(RequestMetadata.from(params));
    }

    @Test
    public void testParseReturnsNullWhenMetaHasNoMcpKeys() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("traceparent", "00-abc-def-01"))
                .build();
        assertNull(RequestMetadata.from(params));
    }

    @Test(expected = RequestMetadata.MCPMetadataValidationException.class)
    public void testParseRejectsProtocolVersionWithoutCapabilities() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "2026-07-28"))
                .build();
        RequestMetadata.from(params);
    }

    @Test(expected = RequestMetadata.MCPMetadataValidationException.class)
    public void testParseRejectsCapabilitiesWithoutProtocolVersion() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()
                                .add("elicitation", Json.createObjectBuilder())))
                .build();
        RequestMetadata.from(params);
    }

    @Test(expected = RequestMetadata.UnsupportedProtocolVersionException.class)
    public void testParseRejectsUnsupportedProtocolVersion() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "9999-01-01")
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()))
                .build();
        RequestMetadata.from(params);
    }

    @Test
    public void testUnsupportedVersionExceptionCarriesRequestedVersion() {
        try {
            JsonObject params = Json.createObjectBuilder()
                    .add("_meta", Json.createObjectBuilder()
                            .add("io.modelcontextprotocol/protocolVersion", "9999-01-01")
                            .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()))
                    .build();
            RequestMetadata.from(params);
        } catch (RequestMetadata.UnsupportedProtocolVersionException e) {
            assertEquals("9999-01-01", e.requestedVersion());
            assertTrue(e.getMessage().contains("9999-01-01"));
            return;
        }
        throw new AssertionError("Expected UnsupportedProtocolVersionException");
    }

    @Test
    public void testClientCapabilitiesParsed() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()
                                .add("elicitation", Json.createObjectBuilder()
                                        .add("form", Json.createObjectBuilder()))
                                .add("roots", Json.createObjectBuilder())))
                .build();

        RequestMetadata metadata = RequestMetadata.from(params);

        assertNotNull(metadata);
        assertEquals(2, metadata.clientCapabilities().size());
        assertTrue(metadata.hasCapability("elicitation"));
        assertTrue(metadata.hasCapability("roots"));
        assertFalse(metadata.hasCapability("sampling"));
    }

    @Test
    public void testExtraMetaFieldsPreserved() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder())
                        .add("traceparent", "00-abc-def-01")
                        .add("custom-key", "custom-value"))
                .build();

        RequestMetadata metadata = RequestMetadata.from(params);

        assertNotNull(metadata);
        assertNotNull(metadata.extra());
        assertEquals(2, metadata.extra().size());
        assertTrue(metadata.extra().containsKey("traceparent"));
        assertTrue(metadata.extra().containsKey("custom-key"));
    }

    @Test
    public void testEmptyCapabilities() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()))
                .build();

        RequestMetadata metadata = RequestMetadata.from(params);

        assertNotNull(metadata);
        assertEquals(ProtocolVersion.V_2026_07_28, metadata.protocolVersion());
        assertTrue(metadata.clientCapabilities().isEmpty());
    }

    @Test
    public void testLegacyVersion() {
        JsonObject params = Json.createObjectBuilder()
                .add("_meta", Json.createObjectBuilder()
                        .add("io.modelcontextprotocol/protocolVersion", "2025-11-25")
                        .add("io.modelcontextprotocol/clientCapabilities", Json.createObjectBuilder()))
                .build();

        RequestMetadata metadata = RequestMetadata.from(params);

        assertNotNull(metadata);
        assertEquals(ProtocolVersion.V_2025_11_25, metadata.protocolVersion());
    }
}
