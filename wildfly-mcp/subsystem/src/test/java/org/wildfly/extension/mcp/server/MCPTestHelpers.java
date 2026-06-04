/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.concurrent.TimeUnit;
import org.wildfly.extension.mcp.api.MCPConnection;

final class MCPTestHelpers {

    private MCPTestHelpers() {
    }

    static JsonObject initializeMessage(int id) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", "initialize")
                .add("params", Json.createObjectBuilder()
                        .add("protocolVersion", "2025-03-26")
                        .add("clientInfo", Json.createObjectBuilder()
                                .add("name", "test-client")
                                .add("version", "1.0.0"))
                        .add("capabilities", Json.createObjectBuilder()))
                .build();
    }

    static JsonObject initializeMessage() {
        return initializeMessage(1);
    }

    static JsonObject jsonRpcRequest(int id, String method) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", id)
                .add("method", method)
                .build();
    }

    static JsonObject jsonRpcNotification(String method) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", method)
                .build();
    }

    static void moveToOperation(MCPMessageHandler handler, TestMCPConnection connection, TestResponder responder) {
        handler.handle(initializeMessage(), connection, responder);
        handler.handle(jsonRpcNotification("notifications/initialized"), connection, responder);
        assertEquals(MCPConnection.Status.IN_OPERATION, connection.status());
        responder.clear();
    }

    static boolean awaitResult(TestResponder responder, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (responder.hasResult()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return responder.hasResult();
    }
}
