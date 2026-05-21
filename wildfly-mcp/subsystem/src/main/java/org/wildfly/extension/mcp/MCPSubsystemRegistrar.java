/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.wildfly.extension.mcp.Capabilities.MCP_CAPABILITY_NAME;
import org.wildfly.extension.mcp.deployment.MCPServerCDIProcessor;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.Phase;
import org.wildfly.extension.mcp.deployment.MCPServerDependencyProcessor;
import org.wildfly.extension.mcp.deployment.MCPServerDeploymentProcessor;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;

/**
 * Registrar for the MCP subsystem.
 */
class MCPSubsystemRegistrar implements SubsystemResourceDefinitionRegistrar {

    static final String NAME = "mcp";
    public static final RuntimeCapability<Void> MCP_CAPABILITY = RuntimeCapability.Builder.of(MCP_CAPABILITY_NAME).setAllowMultipleRegistrations(false).build();
    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of(NAME, Stability.DEFAULT);
    static final ParentResourceDescriptionResolver RESOLVER = new SubsystemResourceDescriptionResolver(NAME, MCPSubsystemRegistrar.class);
    private static final int PHASE_DEPENDENCIES_MCP = 0x1940;
    private static final int PHASE_POST_MODULE_MCP = 0x3840;
    private static final int PHASE_INSTALL_MCP = 8324;

    @Override
    public ManagementResourceRegistration register(SubsystemRegistration parent, ManagementResourceRegistrationContext context) {
        parent.setHostCapable();
        ManagementResourceRegistration registration = parent.registerSubsystemModel(ResourceDefinition.builder(REGISTRATION, RESOLVER).build());
        ResourceDescriptor descriptor = ResourceDescriptor
                .builder(RESOLVER)
                .withDeploymentChainContributor(target -> {
                    target.addDeploymentProcessor(NAME, Phase.DEPENDENCIES, PHASE_DEPENDENCIES_MCP, new MCPServerDependencyProcessor());
                    target.addDeploymentProcessor(NAME, Phase.POST_MODULE, PHASE_POST_MODULE_MCP, new MCPServerCDIProcessor());
                    target.addDeploymentProcessor(NAME, Phase.INSTALL, PHASE_INSTALL_MCP, new MCPServerDeploymentProcessor());
                })
                .addCapability(MCP_CAPABILITY)
                .build();
        ManagementResourceRegistrar.of(descriptor).register(registration);
        new MCPEndpointConfigurationProviderRegistrar(RESOLVER).register(registration, context);
        return registration;
    }
}
