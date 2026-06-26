/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

import org.jboss.as.server.deployment.AttachmentKey;
import org.wildfly.extension.mcp.MCPEndpointConfiguration;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;

public class MCPAttachments {
    static final AttachmentKey<MCPEndpointConfiguration> MCP_ENDPOINT_CONFIGURATION = AttachmentKey.create(MCPEndpointConfiguration.class);
    static final AttachmentKey<WildFlyMCPRegistry> MCP_REGISTRY_METADATA = AttachmentKey.create(WildFlyMCPRegistry.class);
    static final AttachmentKey<Boolean> MCP_OBSERVABLE = AttachmentKey.create(Boolean.class);
}
