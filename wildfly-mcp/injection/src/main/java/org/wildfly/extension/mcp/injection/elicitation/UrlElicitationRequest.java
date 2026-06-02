/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.elicitation;

import java.util.Objects;

/**
 * Describes a URL-mode elicitation request that directs the user to an external URL
 * for out-of-band interaction (e.g. OAuth flows, password entry).
 *
 * <p>Build via {@link #builder(String)}:</p>
 * <pre>{@code
 * UrlElicitationRequest req = UrlElicitationRequest.builder("Please authenticate")
 *     .url("https://example.com/oauth")
 *     .elicitationId("auth-123")
 *     .build();
 * }</pre>
 */
public final class UrlElicitationRequest {

    private final String message;
    private final String url;
    private final String elicitationId;
    private final long timeoutMillis;

    private UrlElicitationRequest(Builder builder) {
        this.message = builder.message;
        this.url = builder.url;
        this.elicitationId = builder.elicitationId;
        this.timeoutMillis = builder.timeoutMillis;
    }

    public String message() {
        return message;
    }

    public String url() {
        return url;
    }

    public String elicitationId() {
        return elicitationId;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public static Builder builder(String message) {
        return new Builder(message);
    }

    public static final class Builder {

        private final String message;
        private String url;
        private String elicitationId;
        private long timeoutMillis = 30_000L;

        public Builder(String message) {
            this.message = Objects.requireNonNull(message, "message must not be null");
        }

        public Builder url(String url) {
            this.url = Objects.requireNonNull(url, "url must not be null");
            return this;
        }

        public Builder elicitationId(String elicitationId) {
            this.elicitationId = Objects.requireNonNull(elicitationId, "elicitationId must not be null");
            return this;
        }

        public Builder timeout(long millis) {
            if (millis <= 0) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeoutMillis = millis;
            return this;
        }

        public UrlElicitationRequest build() {
            Objects.requireNonNull(url, "url must be set");
            Objects.requireNonNull(elicitationId, "elicitationId must be set");
            return new UrlElicitationRequest(this);
        }
    }
}
