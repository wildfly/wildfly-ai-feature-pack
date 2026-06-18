/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;

import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.wildfly.extension.mcp.Capabilities.MCP_CAPABILITY_NAME;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemResourceRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.Bound;
import org.jboss.as.controller.operations.validation.BoundedParameterValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.mcp.deployment.MCPDeploymentRegistrar;
import org.wildfly.extension.mcp.deployment.MCPServerCDIProcessor;
import org.wildfly.extension.mcp.deployment.MCPServerDependencyProcessor;
import org.wildfly.extension.mcp.deployment.MCPServerDeploymentProcessor;
import org.wildfly.subsystem.resource.DurationAttributeDefinition;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.SubsystemResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

/**
 * Registrar for the MCP subsystem.
 */
class MCPSubsystemRegistrar implements SubsystemResourceDefinitionRegistrar {

    static final String NAME = "mcp";
    public static final RuntimeCapability<Void> MCP_CAPABILITY = RuntimeCapability.Builder.of(MCP_CAPABILITY_NAME).setAllowMultipleRegistrations(false).build();
    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of(NAME, Stability.EXPERIMENTAL);
    static final ParentResourceDescriptionResolver RESOLVER = new SubsystemResourceDescriptionResolver(NAME, MCPSubsystemRegistrar.class);
    private static final int PHASE_DEPENDENCIES_MCP = 0x1940;
    private static final int PHASE_POST_MODULE_MCP = 0x3840;
    private static final int PHASE_INSTALL_MCP = 0x2084;

    public static final SimpleAttributeDefinition SSE_PATH = SimpleAttributeDefinitionBuilder.create("sse-path", ModelType.STRING, false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("sse"))
            .setValidator(EndpointPathValidator.INSTANCE)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MESSAGES_PATH = SimpleAttributeDefinitionBuilder.create("messages-path", ModelType.STRING, false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("messages"))
            .setValidator(EndpointPathValidator.INSTANCE)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition STREAMABLE_PATH = SimpleAttributeDefinitionBuilder.create("streamable-path", ModelType.STRING, false)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("streamable"))
            .setValidator(EndpointPathValidator.INSTANCE)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition PAGE_SIZE = SimpleAttributeDefinitionBuilder.create("page-size", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.ZERO)
            .setValidator(BoundedParameterValidator.integerBuilder().withLowerBound(Bound.<Integer>inclusive(0)).build())
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final DurationAttributeDefinition TIMEOUT = DurationAttributeDefinition.builder("timeout", ChronoUnit.SECONDS)
            .setAllowExpression(true)
            .setDefaultValue(Duration.ofMinutes(30))
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(MESSAGES_PATH, SSE_PATH, STREAMABLE_PATH, PAGE_SIZE, TIMEOUT);

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
                .addCapability(Capabilities.MCP_SERVER_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new MCPEndpointConfigurationProviderServiceConfigurator()))
                .build();
        ManagementResourceRegistrar.of(descriptor).register(registration);

        ParentResourceDescriptionResolver deploymentResolver = RESOLVER.createChildResolver(pathElement(DEPLOYMENT));
        ManagementResourceRegistration deploymentRegistration = parent.registerDeploymentModel(
                ResourceDefinition.builder(REGISTRATION, deploymentResolver).asRuntime().asNonFeature().build());
        MCPDeploymentRegistrar.tool(deploymentResolver).register(deploymentRegistration, context);
        MCPDeploymentRegistrar.prompt(deploymentResolver).register(deploymentRegistration, context);
        MCPDeploymentRegistrar.resource(deploymentResolver).register(deploymentRegistration, context);
        MCPDeploymentRegistrar.resourceTemplate(deploymentResolver).register(deploymentRegistration, context);

        return registration;
    }
}
