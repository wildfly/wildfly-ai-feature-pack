/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.resources;

import jakarta.enterprise.context.RequestScoped;

import static org.wildfly.extension.mcp.injection.MCPLogger.ROOT_LOGGER;

@RequestScoped
public class ResourceNotifierBean implements ResourceNotifier {

    @Override
    public void notifyResourceUpdated(String uri) {
        delegate().notifyResourceUpdated(uri);
    }

    private ResourceNotifier delegate() {
        ResourceNotifier notifier = ResourceNotifierHolder.get();
        if (notifier == null) {
            throw ROOT_LOGGER.resourceNotifierNotAvailable();
        }
        return notifier;
    }
}