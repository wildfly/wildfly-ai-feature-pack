/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.util.List;

import static org.wildfly.extension.mcp.api.ClientCapability.ELICITATION;
import static org.wildfly.extension.mcp.api.ClientCapability.FORM;
import static org.wildfly.extension.mcp.api.ClientCapability.URL;

public record InitializeRequest(Implementation implementation, String protocolVersion,
        List<ClientCapability> clientCapabilities) {

    public boolean supportsElicitationForm() {
        return clientCapabilities != null && clientCapabilities.stream()
                .filter(c -> ELICITATION.equals(c.name()))
                .anyMatch(c -> c.properties().isEmpty() || c.properties().containsKey(FORM));
    }

    public boolean supportsElicitationUrl() {
        return clientCapabilities != null && clientCapabilities.stream()
                .filter(c -> ELICITATION.equals(c.name()))
                .anyMatch(c -> c.properties().containsKey(URL));
    }
}