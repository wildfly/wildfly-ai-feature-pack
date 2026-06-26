/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import java.util.Map;
import java.util.Optional;
import org.mcpjava.server.completion.CompletionContext;

record CompleteContextImpl(Map<String, String> arguments) implements CompletionContext {

    @Override
    public Optional<String> getArgument(String name) {
        return Optional.ofNullable(arguments.get(name));
    }
}
