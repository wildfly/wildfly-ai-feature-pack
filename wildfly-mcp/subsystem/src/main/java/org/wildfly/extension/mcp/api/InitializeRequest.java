/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.util.List;

public record InitializeRequest(Implementation implementation, String protocolVersion,
        List<ClientCapability> clientCapabilities) {

    public boolean supportsElicitation() {
        return clientCapabilities != null && clientCapabilities.stream()
                .anyMatch(c -> ClientCapability.ELICITATION.equals(c.name()));
    }

    public boolean supportsElicitationUrl() {
        return clientCapabilities != null && clientCapabilities.stream()
                .filter(c -> ClientCapability.ELICITATION.equals(c.name()))
                .anyMatch(c -> c.properties().containsKey("url"));
    }
}