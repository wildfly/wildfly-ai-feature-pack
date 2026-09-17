/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import jakarta.inject.Inject;

import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.TextResourceContents;
import org.mcpjava.server.tools.Tool;
import org.wildfly.extension.mcp.injection.resources.ResourceNotifier;

public class TestMCPResourceNotifier {

    static final String NOTIFIER_RESOURCE_URI = "test://notifier-data";

    @Inject
    ResourceNotifier injectedNotifier;

    @Resource(uri = NOTIFIER_RESOURCE_URI, mimeType = "text/plain", name = "notifier-data")
    TextResourceContents notifierData() {
        return TextResourceContents.of(NOTIFIER_RESOURCE_URI, "current-data");
    }

    @Tool(name = "notifier-test", description = "Tests resource notification via method parameter injection")
    String notifierTest(ResourceNotifier notifier) {
        notifier.notifyResourceUpdated(NOTIFIER_RESOURCE_URI);
        return "Notified";
    }

    @Tool(name = "notifier-injected-test", description = "Tests resource notification via CDI-injected ResourceNotifier")
    String notifierInjectedTest() {
        injectedNotifier.notifyResourceUpdated(NOTIFIER_RESOURCE_URI);
        return "Notified";
    }
}
