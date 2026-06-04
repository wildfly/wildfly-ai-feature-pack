/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import org.junit.Test;

public class MessagesTestCase {

    @Test
    public void testIntegerId() {
        JsonObject result = Messages.newResult("42", Json.createObjectBuilder());
        assertEquals(JsonValue.ValueType.NUMBER, result.get("id").getValueType());
        assertEquals(42, result.getJsonNumber("id").longValue());
    }

    @Test
    public void testLargeIntegerId() {
        long large = Long.MAX_VALUE;
        JsonObject result = Messages.newResult(String.valueOf(large), Json.createObjectBuilder());
        assertEquals(JsonValue.ValueType.NUMBER, result.get("id").getValueType());
        assertEquals(large, result.getJsonNumber("id").longValue());
    }

    @Test
    public void testStringId() {
        // JSON string id arrives as the toString() of a JsonString: "\"some-id\""
        JsonObject result = Messages.newResult("\"some-id\"", Json.createObjectBuilder());
        assertEquals(JsonValue.ValueType.STRING, result.get("id").getValueType());
        assertEquals("some-id", result.getString("id"));
    }

    @Test
    public void testNullId() {
        JsonObject result = Messages.newResult(null, Json.createObjectBuilder());
        assertEquals(JsonValue.ValueType.NULL, result.get("id").getValueType());
    }

    @Test
    public void testNegativeIntegerId() {
        JsonObject result = Messages.newResult("-1", Json.createObjectBuilder());
        assertEquals(JsonValue.ValueType.NUMBER, result.get("id").getValueType());
        assertEquals(-1L, result.getJsonNumber("id").longValue());
    }

    @Test
    public void testIdPreservedInError() {
        JsonObject error = Messages.newError("99", JsonRPC.INTERNAL_ERROR, "oops");
        assertEquals(JsonValue.ValueType.NUMBER, error.get("id").getValueType());
        assertEquals(99, error.getJsonNumber("id").longValue());
    }

    @Test
    public void testStringIdPreservedInError() {
        JsonObject error = Messages.newError("\"req-abc\"", JsonRPC.INVALID_PARAMS, "bad param");
        assertEquals(JsonValue.ValueType.STRING, error.get("id").getValueType());
        assertEquals("req-abc", error.getString("id"));
    }

    @Test
    public void testFloatIdWrittenAsString() {
        // Float ids (e.g. JSON number 1.5) are not supported. Long.parseLong("1.5") throws, the value
        // is not a quoted string, so it falls through and is written as a JSON string — changing type.
        // This documents the known limitation; the MCP spec uses integer and string ids in practice.
        JsonObject result = Messages.newResult("1.5", Json.createObjectBuilder());
        assertEquals(JsonValue.ValueType.STRING, result.get("id").getValueType());
        assertEquals("1.5", result.getString("id"));
    }
}
