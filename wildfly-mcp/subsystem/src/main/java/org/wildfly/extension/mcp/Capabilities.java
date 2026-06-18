/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.service.descriptor.NullaryServiceDescriptor;

public interface Capabilities {
    String MCP_CAPABILITY_NAME = "org.wildfly.ai.mcp.server";

    NullaryServiceDescriptor<MCPEndpointConfiguration> MCP_SERVER_PROVIDER_DESCRIPTOR = NullaryServiceDescriptor.of("org.wildfly.ai.mcp.server.configuration", MCPEndpointConfiguration.class);
    RuntimeCapability<Void> MCP_SERVER_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of(MCP_SERVER_PROVIDER_DESCRIPTOR).setAllowMultipleRegistrations(false).build();
}
