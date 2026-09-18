/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public class ProtocolVersionTestCase {

    @Test
    public void testFromLegacyVersion() {
        Optional<ProtocolVersion> result = ProtocolVersion.from("2025-11-25");
        assertTrue(result.isPresent());
        assertEquals(ProtocolVersion.V_2025_11_25, result.get());
    }

    @Test
    public void testFromModernVersion() {
        Optional<ProtocolVersion> result = ProtocolVersion.from("2026-07-28");
        assertTrue(result.isPresent());
        assertEquals(ProtocolVersion.V_2026_07_28, result.get());
    }

    @Test
    public void testFromUnknownVersion() {
        Optional<ProtocolVersion> result = ProtocolVersion.from("9999-01-01");
        assertFalse(result.isPresent());
    }

    @Test
    public void testFromNull() {
        Optional<ProtocolVersion> result = ProtocolVersion.from(null);
        assertFalse(result.isPresent());
    }

    @Test
    public void testWireValue() {
        assertEquals("2025-11-25", ProtocolVersion.V_2025_11_25.wireValue());
        assertEquals("2026-07-28", ProtocolVersion.V_2026_07_28.wireValue());
    }

    @Test
    public void testSupportedVersionsContainsAll() {
        assertTrue(ProtocolVersion.SUPPORTED_VERSIONS.contains("2025-03-26"));
        assertTrue(ProtocolVersion.SUPPORTED_VERSIONS.contains("2025-11-25"));
        assertTrue(ProtocolVersion.SUPPORTED_VERSIONS.contains("2026-07-28"));
        assertEquals(3, ProtocolVersion.SUPPORTED_VERSIONS.size());
    }
}
