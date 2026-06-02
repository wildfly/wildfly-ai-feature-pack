/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import java.util.List;
import java.util.Optional;

import static org.wildfly.extension.mcp.api.ClientCapability.ELICITATION;
import static org.wildfly.extension.mcp.api.ClientCapability.FORM;
import static org.wildfly.extension.mcp.api.ClientCapability.URL;

public record InitializeRequest(Implementation implementation, String protocolVersion,
        List<ClientCapability> clientCapabilities) {

    public boolean supportsElicitationForm() {
        return findElicitationCapability()
                .map(c -> c.properties().isEmpty() || c.properties().containsKey(FORM))
                .orElse(false);
    }

    public boolean supportsElicitationUrl() {
        return findElicitationCapability()
                .map(c -> c.properties().containsKey(URL))
                .orElse(false);
    }

    private Optional<ClientCapability> findElicitationCapability() {
        if (clientCapabilities == null) {
            return Optional.empty();
        }
        return clientCapabilities.stream()
                .filter(c -> ELICITATION.equals(c.name()))
                .findFirst();
    }
}