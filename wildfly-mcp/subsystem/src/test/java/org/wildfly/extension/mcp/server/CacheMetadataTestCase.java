/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import jakarta.json.JsonObject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class CacheMetadataTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private TestMCPConnection connection;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("test-tool", MCPFeatureMetadata.builder(MCPFeatureMetadata.Kind.TOOL, "test-tool",
                new MethodMetadata("run", "A test tool", null, null,
                        List.of(new ArgumentMetadata("input", "input", true, String.class)),
                        "org.test.TestTool", "java.lang.String")).build());

        ConnectionManager connectionManager = new ConnectionManager();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(),
                "test-server", "1.0.0",
                new MCPHandlerConfig(0, List.of(), null, 120_000L, "private"));
        responder = new TestResponder();
        connection = new TestMCPConnection("cache-test");
        connectionManager.add(connection);
        MCPTestHelpers.moveToOperation(handler, connection, responder);
    }

    @Test
    public void testDiscoverUsesConfiguredCacheValues() {
        handler.handle(jsonRpcRequest(1, "server/discover"), connection, responder);

        assertTrue(responder.hasResult());
        JsonObject result = responder.lastResult();
        JsonObject meta = result.getJsonObject("_meta");
        assertNotNull("discover should have _meta", meta);
        assertEquals(120_000L, meta.getJsonNumber("ttlMs").longValue());
        assertEquals("private", meta.getString("cacheScope"));
    }

    @Test
    public void testDiscoverDefaultCacheValues() {
        ConnectionManager connectionManager = new ConnectionManager();
        MCPMessageHandler defaultHandler = new MCPMessageHandler(connectionManager,
                new WildFlyMCPRegistry(), getClass().getClassLoader(), "test-server", "1.0.0");
        TestResponder defaultResponder = new TestResponder();
        TestMCPConnection defaultConnection = new TestMCPConnection("default-cache");
        connectionManager.add(defaultConnection);
        MCPTestHelpers.moveToOperation(defaultHandler, defaultConnection, defaultResponder);

        defaultHandler.handle(jsonRpcRequest(2, "server/discover"), defaultConnection, defaultResponder);

        assertTrue(defaultResponder.hasResult());
        JsonObject result = defaultResponder.lastResult();
        JsonObject meta = result.getJsonObject("_meta");
        assertNotNull("discover should have _meta", meta);
        assertEquals(3_600_000L, meta.getJsonNumber("ttlMs").longValue());
        assertEquals("public", meta.getString("cacheScope"));
    }
}
