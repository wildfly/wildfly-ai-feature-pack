/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import org.wildfly.extension.mcp.injection.resources.ResourceNotifier;

class ResourceNotifierImpl implements ResourceNotifier {

    private final ResourceMessageHandler resourceHandler;

    ResourceNotifierImpl(ResourceMessageHandler resourceHandler) {
        this.resourceHandler = resourceHandler;
    }

    @Override
    public void notifyResourceUpdated(String uri) {
        resourceHandler.notifyResourceUpdated(uri);
    }
}
