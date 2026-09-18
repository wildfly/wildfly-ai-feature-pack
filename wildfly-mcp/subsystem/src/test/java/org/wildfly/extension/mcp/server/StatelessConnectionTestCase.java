/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ClientCapability;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.ProtocolVersion;
import org.wildfly.extension.mcp.api.RequestMetadata;

public class StatelessConnectionTestCase {

    @Test
    public void testStatusAlwaysInOperation() {
        StatelessConnection conn = new StatelessConnection(makeMetadata());
        assertEquals(MCPConnection.Status.IN_OPERATION, conn.status());
    }

    @Test
    public void testInitializeReturnsFalse() {
        StatelessConnection conn = new StatelessConnection(makeMetadata());
        assertFalse(conn.initialize(null));
    }

    @Test
    public void testSetInitializedReturnsFalse() {
        StatelessConnection conn = new StatelessConnection(makeMetadata());
        assertFalse(conn.setInitialized());
    }

    @Test
    public void testIdIsUnique() {
        StatelessConnection conn1 = new StatelessConnection(makeMetadata());
        StatelessConnection conn2 = new StatelessConnection(makeMetadata());
        assertNotEquals(conn1.id(), conn2.id());
    }

    @Test
    public void testInitializeRequestFromMetadata() {
        StatelessConnection conn = new StatelessConnection(makeMetadata());
        assertNotNull(conn.initializeRequest());
        assertEquals("2026-07-28", conn.initializeRequest().protocolVersion());
    }

    @Test
    public void testInitializeRequestNullWhenNoMetadata() {
        StatelessConnection conn = new StatelessConnection(null);
        assertNull(conn.initializeRequest());
    }

    @Test
    public void testCloseIsNoOp() {
        StatelessConnection conn = new StatelessConnection(makeMetadata());
        conn.close();
        assertEquals(MCPConnection.Status.IN_OPERATION, conn.status());
    }

    private RequestMetadata makeMetadata() {
        return new RequestMetadata(
                ProtocolVersion.V_2026_07_28,
                List.of(new ClientCapability("elicitation", java.util.Set.of())),
                java.util.Map.of());
    }
}
