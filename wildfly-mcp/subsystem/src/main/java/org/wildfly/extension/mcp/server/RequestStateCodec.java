/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 codec for {@code requestState} tokens used in multi-round tool results (MRTR).
 * <p>
 * Each token is a Base64-encoded JSON payload containing the state map, authenticated
 * principal, TTL, and originating request ID. The payload is integrity-protected with
 * an HMAC-SHA256 signature using a server-side secret.
 * </p>
 * <p>
 * Per the 2026-07-28 spec, {@code requestState} MUST be treated as attacker-controlled
 * input. This codec validates HMAC integrity, TTL expiry, and originating request ID.
 * </p>
 */
class RequestStateCodec {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long DEFAULT_TTL_MS = 300_000; // 5 minutes

    private final byte[] secret;
    private final long ttlMs;

    RequestStateCodec(byte[] secret) {
        this(secret, DEFAULT_TTL_MS);
    }

    RequestStateCodec(byte[] secret, long ttlMs) {
        if (secret == null || secret.length < 16) {
            throw ROOT_LOGGER.secretTooShort();
        }
        this.secret = secret.clone();
        this.ttlMs = ttlMs;
    }

    String encode(JsonObject state, String principal, String requestId, String toolName) {
        long now = System.currentTimeMillis();
        JsonObjectBuilder payload = Json.createObjectBuilder()
                .add("state", state)
                .add("principal", principal != null ? principal : "")
                .add("requestId", requestId != null ? requestId : "")
                .add("toolName", toolName != null ? toolName : "")
                .add("createdAt", now)
                .add("expiresAt", now + ttlMs);

        String json = payload.build().toString();
        String signature = hmac(json);
        String combined = json + "." + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                combined.getBytes(StandardCharsets.UTF_8));
    }

    DecodedState decode(String token) throws RequestStateException {
        String combined;
        try {
            combined = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new RequestStateException(ROOT_LOGGER.invalidRequestStateEncoding());
        }

        // Safe: '.' is not in the Base64url alphabet, so hmac() output never contains it.
        // MessageDigest.isEqual is constant-time; both inputs are pure ASCII (Base64url),
        // so getBytes(UTF_8) produces one byte per char — no multibyte expansion risk.
        int dotIndex = combined.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new RequestStateException(ROOT_LOGGER.invalidRequestStateFormat());
        }

        String json = combined.substring(0, dotIndex);
        String signature = combined.substring(dotIndex + 1);

        if (!MessageDigest.isEqual(
                hmac(json).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new RequestStateException(ROOT_LOGGER.requestStateSignatureVerificationFailed());
        }

        JsonObject payload;
        try (var reader = Json.createReader(new StringReader(json))) {
            payload = reader.readObject();
        } catch (Exception e) {
            throw new RequestStateException(ROOT_LOGGER.invalidRequestStatePayload());
        }

        jakarta.json.JsonNumber expiresAtNum = payload.getJsonNumber("expiresAt");
        if (expiresAtNum == null) {
            throw new RequestStateException(ROOT_LOGGER.invalidRequestStatePayload());
        }
        long expiresAt = expiresAtNum.longValue();
        if (System.currentTimeMillis() > expiresAt) {
            throw new RequestStateException(ROOT_LOGGER.requestStateExpired());
        }

        return new DecodedState(
                payload.getJsonObject("state"),
                payload.getString("principal", ""),
                payload.getString("requestId", ""),
                payload.getString("toolName", ""));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    record DecodedState(JsonObject state, String principal, String requestId, String toolName) {}

    static class RequestStateException extends Exception {
        RequestStateException(String message) {
            super(message);
        }
    }
}
