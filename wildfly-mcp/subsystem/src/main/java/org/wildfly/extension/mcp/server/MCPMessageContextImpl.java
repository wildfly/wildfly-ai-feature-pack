/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.IdentityHashMap;
import java.util.Map;

import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.MCPContextKey;
import org.wildfly.extension.mcp.api.MCPMessageContext;

/**
 * Mutable implementation of {@link MCPMessageContext}.
 * <p>
 * Setter methods are package-private so that only the framework ({@link MCPMessageHandler})
 * can mutate context state. Listeners receive the read-only {@link MCPMessageContext} interface.
 * </p>
 */
class MCPMessageContextImpl implements MCPMessageContext {

    private final String method;
    private final String connectionId;
    private final String requestId;
    private final MCPConnection.Status connectionStatus;
    private final long startTimeNanos;
    private long durationNanos;
    private int errorCode;
    private String errorMessage;
    private Map<String, String> propagationHeaders;
    private String genAiTarget;
    private String protocolVersion;
    private String clientAddress;
    private int clientPort = -1;
    private String networkProtocolVersion;
    private String resourceUri;
    // All listener callbacks for a given message are invoked sequentially on the same thread,
    // so IdentityHashMap is safe here without synchronization.
    private final Map<MCPContextKey<?>, Object> attributes = new IdentityHashMap<>();

    MCPMessageContextImpl(String method, String connectionId, String requestId,
                          MCPConnection.Status connectionStatus, long startTimeNanos) {
        this.method = method;
        this.connectionId = connectionId;
        this.requestId = requestId;
        this.connectionStatus = connectionStatus;
        this.startTimeNanos = startTimeNanos;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String connectionId() {
        return connectionId;
    }

    @Override
    public String requestId() {
        return requestId;
    }

    @Override
    public String connectionStatus() {
        return connectionStatus.name();
    }

    @Override
    public long startTimeNanos() {
        return startTimeNanos;
    }

    @Override
    public long durationNanos() {
        return durationNanos;
    }

    void setDurationNanos(long durationNanos) {
        this.durationNanos = durationNanos;
    }

    @Override
    public int errorCode() {
        return errorCode;
    }

    @Override
    public boolean hasError() {
        return errorCode != 0;
    }

    void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String errorMessage() {
        return errorMessage;
    }

    void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public Map<String, String> propagationHeaders() {
        return propagationHeaders;
    }

    void setPropagationHeaders(Map<String, String> propagationHeaders) {
        this.propagationHeaders = propagationHeaders;
    }

    @Override
    public String genAiTarget() {
        return genAiTarget;
    }

    void setGenAiTarget(String genAiTarget) {
        this.genAiTarget = genAiTarget;
    }

    @Override
    public String protocolVersion() {
        return protocolVersion;
    }

    void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public String clientAddress() {
        return clientAddress;
    }

    void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    @Override
    public int clientPort() {
        return clientPort;
    }

    void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    @Override
    public String networkProtocolVersion() {
        return networkProtocolVersion;
    }

    void setNetworkProtocolVersion(String networkProtocolVersion) {
        this.networkProtocolVersion = networkProtocolVersion;
    }

    @Override
    public String resourceUri() {
        return resourceUri;
    }

    void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    @Override
    public <T> void setAttribute(MCPContextKey<T> key, T value) {
        attributes.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(MCPContextKey<T> key) {
        return (T) attributes.get(key);
    }
}
