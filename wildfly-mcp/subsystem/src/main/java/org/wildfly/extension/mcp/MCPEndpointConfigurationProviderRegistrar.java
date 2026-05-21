/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp;




import java.util.Collection;
import java.util.List;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;


public class MCPEndpointConfigurationProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition SSE_PATH = SimpleAttributeDefinitionBuilder.create("sse-path", ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition MESSAGES_PATH = SimpleAttributeDefinitionBuilder.create("messages-path", ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition STREAMABLE_PATH = SimpleAttributeDefinitionBuilder.create("streamable-path", ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition PAGE_SIZE = SimpleAttributeDefinitionBuilder.create("page-size", ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new org.jboss.dmr.ModelNode(0))
            .setRestartAllServices()
            .build();
    public static final SimpleAttributeDefinition TIMEOUT = SimpleAttributeDefinitionBuilder.create("timeout", ModelType.LONG, true)
            .setAllowExpression(true)
            .setDefaultValue(new org.jboss.dmr.ModelNode(1800L))
            .setRestartAllServices()
            .build();
    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(MESSAGES_PATH, SSE_PATH, STREAMABLE_PATH, PAGE_SIZE, TIMEOUT);

    private final ResourceDescriptor descriptor;
    static final String NAME = "mcp-server";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public MCPEndpointConfigurationProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(Capabilities.MCP_SERVER_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new MCPEndpointConfigurationProviderServiceConfigurator()))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext mrrc) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }

}
