/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.extension.mcp.server.TestResponder;

class JsonRPCTestCase {

    private TestResponder responder;

    @BeforeEach
    void setUp() {
        responder = new TestResponder();
    }

    // --- validate() ---

    @Test
    void validateAcceptsValidRequest() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("method", "tools/list")
                .build();
        assertTrue(JsonRPC.validate(message, responder));
        assertNull(responder.lastMessage());
    }

    @Test
    void validateAcceptsValidNotification() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", "notifications/initialized")
                .build();
        assertTrue(JsonRPC.validate(message, responder));
    }

    @Test
    void validateAcceptsValidResponse() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .add("result", Json.createObjectBuilder())
                .build();
        assertTrue(JsonRPC.validate(message, responder));
    }

    @Test
    void validateRejectsWrongVersion() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "1.0")
                .add("id", 1)
                .add("method", "tools/list")
                .build();
        assertFalse(JsonRPC.validate(message, responder));
        assertTrue(responder.hasError());
        JsonObject error = responder.lastError();
        assertEquals(JsonRPC.INVALID_REQUEST, error.getInt("code"));
    }

    @Test
    void validateRejectsMissingJsonrpcField() {
        JsonObject message = Json.createObjectBuilder()
                .add("id", 1)
                .add("method", "tools/list")
                .build();
        assertFalse(JsonRPC.validate(message, responder));
        assertTrue(responder.hasError());
        JsonObject error = responder.lastError();
        assertEquals(JsonRPC.INVALID_REQUEST, error.getInt("code"));
    }

    @Test
    void validateRejectsMissingMethodOnRequest() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", 1)
                .build();
        assertFalse(JsonRPC.validate(message, responder));
        assertTrue(responder.hasError());
        JsonObject error = responder.lastError();
        assertEquals(JsonRPC.METHOD_NOT_FOUND, error.getInt("code"));
    }

    @Test
    void validatePassesIdInErrorResponse() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "1.0")
                .add("id", 42)
                .add("method", "tools/list")
                .build();
        assertFalse(JsonRPC.validate(message, responder));
        JsonObject last = responder.lastMessage();
        assertEquals(42, last.getJsonNumber("id").longValue());
    }

    @Test
    void validateHandlesNullIdGracefully() {
        JsonObject message = Json.createObjectBuilder()
                .add("jsonrpc", "1.0")
                .add("method", "tools/list")
                .build();
        assertFalse(JsonRPC.validate(message, responder));
        JsonObject last = responder.lastMessage();
        assertTrue(last.isNull("id"));
    }

    // --- convertMap() ---

    @Test
    void convertMapHandlesString() {
        Map<String, Object> map = Map.of("key", "value");
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals("value", result.getString("key"));
    }

    @Test
    void convertMapHandlesInteger() {
        Map<String, Object> map = Map.of("count", 42);
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals(42, result.getInt("count"));
    }

    @Test
    void convertMapHandlesLong() {
        Map<String, Object> map = Map.of("big", 100_000_000_000L);
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals(100_000_000_000L, result.getJsonNumber("big").longValue());
    }

    @Test
    void convertMapHandlesDouble() {
        Map<String, Object> map = Map.of("pi", 3.14);
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals(3.14, result.getJsonNumber("pi").doubleValue(), 0.001);
    }

    @Test
    void convertMapHandlesBoolean() {
        Map<String, Object> map = Map.of("flag", true);
        JsonObject result = JsonRPC.convertMap(map).build();
        assertTrue(result.getBoolean("flag"));
    }

    @Test
    void convertMapHandlesBigDecimal() {
        Map<String, Object> map = Map.of("precise", new BigDecimal("1.23456789"));
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals(new BigDecimal("1.23456789"), result.getJsonNumber("precise").bigDecimalValue());
    }

    @Test
    void convertMapHandlesBigInteger() {
        Map<String, Object> map = Map.of("huge", new BigInteger("99999999999999999999"));
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals(new BigInteger("99999999999999999999"), result.getJsonNumber("huge").bigIntegerValue());
    }

    @Test
    void convertMapHandlesJsonValue() {
        Map<String, Object> map = Map.of("nested", Json.createValue("hello"));
        JsonObject result = JsonRPC.convertMap(map).build();
        assertEquals("hello", result.getString("nested"));
    }

    @Test
    void convertMapHandlesNull() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("empty", null);
        JsonObject result = JsonRPC.convertMap(map).build();
        assertTrue(result.isNull("empty"));
    }

    @Test
    void convertMapHandlesNestedMap() {
        Map<String, Object> inner = Map.of("a", "b");
        Map<String, Object> outer = Map.of("nested", inner);
        JsonObject result = JsonRPC.convertMap(outer).build();
        assertNotNull(result.getJsonObject("nested"));
        assertEquals("b", result.getJsonObject("nested").getString("a"));
    }

    @Test
    void convertMapHandlesEmptyMap() {
        JsonObject result = JsonRPC.convertMap(Map.of()).build();
        assertTrue(result.isEmpty());
    }
}
