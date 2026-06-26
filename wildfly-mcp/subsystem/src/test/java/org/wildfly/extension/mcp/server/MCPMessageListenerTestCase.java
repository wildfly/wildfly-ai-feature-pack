/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.wildfly.extension.mcp.server.MCPTestHelpers.initializeMessage;
import static org.wildfly.extension.mcp.server.MCPTestHelpers.jsonRpcRequest;

import org.wildfly.extension.mcp.api.ConnectionManager;
import org.wildfly.extension.mcp.api.JsonRPC;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.MCPContextKey;
import org.wildfly.extension.mcp.api.MCPMessageContext;
import org.wildfly.extension.mcp.api.MCPMessageListener;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class MCPMessageListenerTestCase {

    private MCPMessageHandler handler;
    private TestResponder responder;
    private TestMCPConnection connection;
    private ConnectionManager connectionManager;
    private RecordingListener listener;

    @Before
    public void setUp() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();

        registry.addTool("echo", new MCPFeatureMetadata(
                MCPFeatureMetadata.Kind.TOOL, "echo",
                new MethodMetadata("echo", "Echoes the input", null, null,
                        List.of(),
                        "org.test.EchoTool", "java.lang.String")));

        connectionManager = new ConnectionManager();
        listener = new RecordingListener();
        handler = new MCPMessageHandler(connectionManager, registry, getClass().getClassLoader(),
                "test-server", "1.0.0", 0, List.of(listener));

        responder = new TestResponder();
        connection = new TestMCPConnection("test-conn-1");
        connectionManager.add(connection);
    }

    @Test
    public void testListenerCalledOnInitialize() {
        handler.handle(initializeMessage(1), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(1, listener.afterCount());
        assertEquals(0, listener.errorCount());

        MCPMessageContext ctx = listener.lastBeforeContext();
        assertEquals("initialize", ctx.method());
        assertEquals("test-conn-1", ctx.connectionId());
        assertEquals("NEW", ctx.connectionStatus());
        // durationNanos is set after the handler returns, so assert on the afterContext
        assertTrue(listener.lastAfterContext().durationNanos() > 0);
    }

    @Test
    public void testListenerCalledOnPing() {
        moveToOperation();
        listener.clear();

        handler.handle(jsonRpcRequest(2, "ping"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(1, listener.afterCount());
        assertEquals(0, listener.errorCount());

        MCPMessageContext ctx = listener.lastBeforeContext();
        assertEquals("ping", ctx.method());
        assertEquals("IN_OPERATION", ctx.connectionStatus());
    }

    @Test
    public void testListenerCalledOnToolsList() {
        moveToOperation();
        listener.clear();

        handler.handle(jsonRpcRequest(3, "tools/list"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(1, listener.afterCount());
        assertEquals("tools/list", listener.lastBeforeContext().method());
    }

    @Test
    public void testListenerCalledOnShutdown() {
        moveToOperation();
        connection.close();
        assertEquals(MCPConnection.Status.SHUTDOWN, connection.status());
        listener.clear();

        handler.handle(jsonRpcRequest(10, "ping"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(1, listener.errorCount());

        MCPMessageContext ctx = listener.lastErrorContext();
        assertEquals("ping", ctx.method());
        assertEquals("SHUTDOWN", ctx.connectionStatus());
        assertEquals(JsonRPC.INTERNAL_ERROR, ctx.errorCode());
    }

    @Test
    public void testListenerNotCalledOnResponseMessages() {
        moveToOperation();
        listener.clear();

        JsonObject responseMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 99)
                .add("result", Json.createObjectBuilder())
                .build();
        handler.handle(responseMessage, connection, responder);

        assertEquals(0, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(0, listener.errorCount());
    }

    @Test
    public void testListenerContextCarriesRequestId() {
        moveToOperation();
        listener.clear();

        handler.handle(jsonRpcRequest(42, "ping"), connection, responder);

        assertEquals("42", listener.lastBeforeContext().requestId());
    }

    @Test
    public void testListenerContextAttributes() {
        MCPContextKey<String> testKey = MCPContextKey.of("test.key");
        RecordingListener attrListener = new RecordingListener() {
            @Override
            public void onBeforeMessageDispatched(MCPMessageContext context) {
                super.onBeforeMessageDispatched(context);
                context.setAttribute(testKey, "test.value");
            }

            @Override
            public void onAfterMessageDispatched(MCPMessageContext context) {
                super.onAfterMessageDispatched(context);
                // Verify that attributes survive from onBeforeMessageDispatched to onAfterMessageDispatched
                assertEquals("test.value", context.getAttribute(testKey));
            }
        };

        MCPMessageHandler handlerWithAttr = new MCPMessageHandler(connectionManager,
                new WildFlyMCPRegistry(), getClass().getClassLoader(),
                "test-server", "1.0.0", 0, List.of(attrListener));
        TestMCPConnection conn2 = new TestMCPConnection("test-conn-2");
        connectionManager.add(conn2);
        TestResponder resp2 = new TestResponder();
        MCPTestHelpers.moveToOperation(handlerWithAttr, conn2, resp2);
        resp2.clear();
        attrListener.clear();

        handlerWithAttr.handle(jsonRpcRequest(1, "ping"), conn2, resp2);

        assertEquals(1, attrListener.afterCount());
    }

    @Test
    public void testThrowingListenerDoesNotBreakHandling() {
        MCPMessageListener throwingListener = new MCPMessageListener() {
            @Override
            public void onBeforeMessageDispatched(MCPMessageContext context) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onAfterMessageDispatched(MCPMessageContext context) {
                throw new RuntimeException("boom");
            }
        };

        MCPMessageHandler handlerWithBadListener = new MCPMessageHandler(connectionManager,
                new WildFlyMCPRegistry(), getClass().getClassLoader(),
                "test-server", "1.0.0", 0, List.of(throwingListener, listener));

        TestMCPConnection conn3 = new TestMCPConnection("test-conn-3");
        connectionManager.add(conn3);
        TestResponder resp3 = new TestResponder();

        handlerWithBadListener.handle(initializeMessage(1), conn3, resp3);

        assertTrue(resp3.hasResult());
        assertEquals(1, listener.beforeCount());
        assertEquals(1, listener.afterCount());
    }

    @Test
    public void testMultipleListeners() {
        RecordingListener listener2 = new RecordingListener();

        MCPMessageHandler multiHandler = new MCPMessageHandler(connectionManager,
                new WildFlyMCPRegistry(), getClass().getClassLoader(),
                "test-server", "1.0.0", 0, List.of(listener, listener2));

        TestMCPConnection conn4 = new TestMCPConnection("test-conn-4");
        connectionManager.add(conn4);
        TestResponder resp4 = new TestResponder();

        multiHandler.handle(initializeMessage(1), conn4, resp4);

        assertEquals(1, listener.beforeCount());
        assertEquals(1, listener2.beforeCount());
        assertEquals(1, listener.afterCount());
        assertEquals(1, listener2.afterCount());
    }

    @Test
    public void testListenerErrorCalledOnUnsupportedMethod() {
        moveToOperation();
        listener.clear();

        handler.handle(jsonRpcRequest(99, "unsupported/method"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(1, listener.errorCount());
        MCPMessageContext ctx = listener.lastErrorContext();
        assertEquals("unsupported/method", ctx.method());
        assertEquals(JsonRPC.METHOD_NOT_FOUND, ctx.errorCode());
    }

    @Test
    public void testListenerErrorCalledOnWrongFirstMessage() {
        // NEW state: first message must be "initialize"
        handler.handle(jsonRpcRequest(1, "ping"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(1, listener.errorCount());
        MCPMessageContext ctx = listener.lastErrorContext();
        assertEquals(JsonRPC.METHOD_NOT_FOUND, ctx.errorCode());
        assertTrue(responder.hasError());
    }

    @Test
    public void testListenerErrorCalledOnInitializeWithoutParams() {
        JsonObject noParams = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "initialize")
                .build();
        handler.handle(noParams, connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(1, listener.errorCount());
        assertEquals(JsonRPC.INVALID_PARAMS, listener.lastErrorContext().errorCode());
        assertTrue(responder.hasError());
    }

    @Test
    public void testListenerErrorCalledOnUnexpectedMessageDuringInitializing() {
        // Advance to INITIALIZING state
        handler.handle(initializeMessage(1), connection, responder);
        assertEquals(MCPConnection.Status.INITIALIZING, connection.status());
        listener.clear();
        responder.clear();

        handler.handle(jsonRpcRequest(2, "tools/list"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(1, listener.errorCount());
        assertEquals(JsonRPC.INTERNAL_ERROR, listener.lastErrorContext().errorCode());
        assertTrue(responder.hasError());
    }

    @Test
    public void testListenerErrorCalledOnQCloseFailure() {
        moveToOperation();
        // Pre-remove so connectionManager.remove() returns false, triggering the error path
        connectionManager.remove(connection.id());
        listener.clear();
        responder.clear();

        handler.handle(jsonRpcRequest(5, "q/close"), connection, responder);

        assertEquals(1, listener.beforeCount());
        assertEquals(0, listener.afterCount());
        assertEquals(1, listener.errorCount());
        MCPMessageContext ctx = listener.lastErrorContext();
        assertEquals("q/close", ctx.method());
        assertEquals(JsonRPC.INTERNAL_ERROR, ctx.errorCode());
        assertTrue(responder.hasError());
    }

    @Test
    public void testThrowingOnErrorListenerDoesNotBreakOtherListeners() {
        MCPMessageListener throwingOnErrorListener = new MCPMessageListener() {
            @Override
            public void onBeforeMessageDispatched(MCPMessageContext context) {}

            @Override
            public void onAfterMessageDispatched(MCPMessageContext context) {}

            @Override
            public void onError(MCPMessageContext context, Throwable error) {
                throw new RuntimeException("boom in onError");
            }
        };

        MCPMessageHandler handlerWithBadErrorListener = new MCPMessageHandler(connectionManager,
                new WildFlyMCPRegistry(), getClass().getClassLoader(),
                "test-server", "1.0.0", 0, List.of(throwingOnErrorListener, listener));

        TestMCPConnection conn5 = new TestMCPConnection("test-conn-5");
        connectionManager.add(conn5);
        TestResponder resp5 = new TestResponder();

        // First message to NEW connection must be "initialize"; sending "ping" triggers METHOD_NOT_FOUND
        handlerWithBadErrorListener.handle(jsonRpcRequest(1, "ping"), conn5, resp5);

        assertEquals(1, listener.errorCount());
        assertEquals(0, listener.afterCount());
    }

    @Test
    public void testHandlerWorksWithNoListeners() {
        MCPMessageHandler noListenerHandler = new MCPMessageHandler(connectionManager,
                new WildFlyMCPRegistry(), getClass().getClassLoader(),
                "test-server", "1.0.0", 0, List.of());
        TestMCPConnection conn = new TestMCPConnection("no-listener-conn");
        connectionManager.add(conn);
        TestResponder resp = new TestResponder();

        noListenerHandler.handle(initializeMessage(1), conn, resp);
        assertTrue(resp.hasResult());

        noListenerHandler.handle(MCPTestHelpers.jsonRpcNotification("notifications/initialized"), conn, resp);
        assertEquals(MCPConnection.Status.IN_OPERATION, conn.status());

        resp.clear();
        noListenerHandler.handle(jsonRpcRequest(2, "ping"), conn, resp);
        assertTrue(resp.hasResult());
    }

    private void moveToOperation() {
        MCPTestHelpers.moveToOperation(handler, connection, responder);
    }

    static class RecordingListener implements MCPMessageListener {
        private final List<MCPMessageContext> beforeContexts = new ArrayList<>();
        private final List<MCPMessageContext> afterContexts = new ArrayList<>();
        private final List<MCPMessageContext> errorContexts = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onBeforeMessageDispatched(MCPMessageContext context) {
            beforeContexts.add(context);
        }

        @Override
        public void onAfterMessageDispatched(MCPMessageContext context) {
            afterContexts.add(context);
        }

        @Override
        public void onError(MCPMessageContext context, Throwable error) {
            errorContexts.add(context);
            errors.add(error);
        }

        int beforeCount() { return beforeContexts.size(); }
        int afterCount() { return afterContexts.size(); }
        int errorCount() { return errorContexts.size(); }

        MCPMessageContext lastBeforeContext() {
            return beforeContexts.isEmpty() ? null : beforeContexts.get(beforeContexts.size() - 1);
        }

        MCPMessageContext lastAfterContext() {
            return afterContexts.isEmpty() ? null : afterContexts.get(afterContexts.size() - 1);
        }

        MCPMessageContext lastErrorContext() {
            return errorContexts.isEmpty() ? null : errorContexts.get(errorContexts.size() - 1);
        }

        void clear() {
            beforeContexts.clear();
            afterContexts.clear();
            errorContexts.clear();
            errors.clear();
        }
    }
}
