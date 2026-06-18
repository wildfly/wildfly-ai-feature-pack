/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.wildfly.extension.mcp.Capabilities.MCP_SERVER_PROVIDER_CAPABILITY;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.MESSAGES_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.PAGE_SIZE;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.SSE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.STREAMABLE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.TIMEOUT;

import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.Installer;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

public class MCPEndpointConfigurationProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        final String ssePath = SSE_PATH.resolveModelAttribute(context, model).asString();
        final String messagesPath = MESSAGES_PATH.resolveModelAttribute(context, model).asString();
        final String streamablePath = STREAMABLE_PATH.resolveModelAttribute(context, model).asString();
        final int pageSize = PAGE_SIZE.resolveModelAttribute(context, model).asInt(0);
        final long timeout = TIMEOUT.resolve(context, model).getSeconds();
        Supplier<MCPEndpointConfiguration> factory = new Supplier<>() {
            @Override
            public MCPEndpointConfiguration get() {
                return new MCPEndpointConfiguration(ssePath, messagesPath, streamablePath, pageSize, timeout);
            }
        };
        return CapabilityServiceInstaller.BlockingBuilder.of(MCP_SERVER_PROVIDER_CAPABILITY, factory)
                .startWhen(Installer.StartWhen.AVAILABLE)
                .build();
    }

}
