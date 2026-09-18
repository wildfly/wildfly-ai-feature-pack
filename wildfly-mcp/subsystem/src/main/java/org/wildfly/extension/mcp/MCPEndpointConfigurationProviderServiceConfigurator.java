/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.wildfly.extension.mcp.Capabilities.MCP_SERVER_PROVIDER_CAPABILITY;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.MESSAGES_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.PAGE_SIZE;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.SSE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.REQUEST_STATE_SECRET;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.ALLOWED_ORIGINS;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.STREAMABLE_PATH;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.TIMEOUT;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.CACHE_TTL;
import static org.wildfly.extension.mcp.MCPSubsystemRegistrar.CACHE_SCOPE;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        ModelNode secretNode = REQUEST_STATE_SECRET.resolveModelAttribute(context, model);
        final String requestStateSecret = secretNode.isDefined() ? secretNode.asString() : null;
        List<String> originsList = ALLOWED_ORIGINS.unwrap(context, model);
        final Set<String> allowedOrigins = originsList.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(originsList));
        final long cacheTtlMs = CACHE_TTL.resolveModelAttribute(context, model).asLong(3_600_000L);
        final String cacheScope = CACHE_SCOPE.resolveModelAttribute(context, model).asString("public");
        Supplier<MCPEndpointConfiguration> factory = new Supplier<>() {
            @Override
            public MCPEndpointConfiguration get() {
                return new MCPEndpointConfiguration(ssePath, messagesPath, streamablePath, pageSize, timeout, requestStateSecret, allowedOrigins, cacheTtlMs, cacheScope);
            }
        };
        return CapabilityServiceInstaller.BlockingBuilder.of(MCP_SERVER_PROVIDER_CAPABILITY, factory)
                .startWhen(Installer.StartWhen.AVAILABLE)
                .build();
    }

}
