/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

public class RequestStateCodecTestCase {

    private static final byte[] SECRET = "test-secret-key-at-least-16-bytes".getBytes();
    private RequestStateCodec codec;

    @Before
    public void setUp() {
        codec = new RequestStateCodec(SECRET);
    }

    @Test
    public void testEncodeAndDecode() throws Exception {
        JsonObject state = Json.createObjectBuilder()
                .add("toolName", "echo")
                .add("retryCount", 1)
                .build();

        String token = codec.encode(state, "user@example.com", "req-123", "echo");
        RequestStateCodec.DecodedState decoded = codec.decode(token);

        assertNotNull(decoded);
        assertEquals("echo", decoded.state().getString("toolName"));
        assertEquals(1, decoded.state().getInt("retryCount"));
        assertEquals("user@example.com", decoded.principal());
        assertEquals("req-123", decoded.requestId());
        assertEquals("echo", decoded.toolName());
    }

    @Test
    public void testNullPrincipalAndRequestId() throws Exception {
        JsonObject state = Json.createObjectBuilder().add("key", "value").build();

        String token = codec.encode(state, null, null, null);
        RequestStateCodec.DecodedState decoded = codec.decode(token);

        assertEquals("", decoded.principal());
        assertEquals("", decoded.requestId());
        assertEquals("", decoded.toolName());
    }

    @Test(expected = RequestStateCodec.RequestStateException.class)
    public void testTamperedToken() throws Exception {
        JsonObject state = Json.createObjectBuilder().add("key", "value").build();
        String token = codec.encode(state, "user", "req-1", "testTool");

        String tampered = token.substring(0, token.length() - 2) + "XX";
        codec.decode(tampered);
    }

    @Test(expected = RequestStateCodec.RequestStateException.class)
    public void testDifferentSecret() throws Exception {
        JsonObject state = Json.createObjectBuilder().add("key", "value").build();
        String token = codec.encode(state, "user", "req-1", "testTool");

        RequestStateCodec otherCodec = new RequestStateCodec(
                "different-secret-key-16-bytes!!".getBytes());
        otherCodec.decode(token);
    }

    @Test(expected = RequestStateCodec.RequestStateException.class)
    public void testExpiredToken() throws Exception {
        RequestStateCodec shortLivedCodec = new RequestStateCodec(SECRET, 1);
        JsonObject state = Json.createObjectBuilder().add("key", "value").build();
        String token = shortLivedCodec.encode(state, "user", "req-1", "testTool");

        Thread.sleep(10);
        shortLivedCodec.decode(token);
    }

    @Test(expected = RequestStateCodec.RequestStateException.class)
    public void testInvalidBase64() throws Exception {
        codec.decode("not-valid-base64!!!");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testShortSecret() {
        new RequestStateCodec("short".getBytes());
    }

    @Test
    public void testEncodeReturnsNonNullToken() throws Exception {
        JsonObject state = Json.createObjectBuilder().add("key", "value").build();
        String token = codec.encode(state, "user", "req-1", "testTool");
        assertNotNull(token);
    }
}
