/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

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
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;

public class MCPDeploymentRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition DESCRIPTION = SimpleAttributeDefinitionBuilder
            .create("description", ModelType.STRING, true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition TITLE = SimpleAttributeDefinitionBuilder
            .create("title", ModelType.STRING, true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MIME_TYPE = SimpleAttributeDefinitionBuilder
            .create("mime-type", ModelType.STRING, true)
            .setStability(Stability.EXPERIMENTAL)
            .build();

    static final String TOOL = "tool";
    static final String PROMPT = "prompt";
    static final String RESOURCE = "resource";
    static final String RESOURCE_TEMPLATE = "resource-template";

    private static final Collection<AttributeDefinition> TOOL_ATTRIBUTES = List.of(DESCRIPTION, TITLE);
    private static final Collection<AttributeDefinition> PROMPT_ATTRIBUTES = List.of(DESCRIPTION);
    private static final Collection<AttributeDefinition> RESOURCE_ATTRIBUTES = List.of(DESCRIPTION, MIME_TYPE, TITLE);

    private final String name;
    private final ResourceRegistration registration;
    private final ResourceDescriptor descriptor;

    MCPDeploymentRegistrar(String name, ParentResourceDescriptionResolver parentResolver,
            Collection<AttributeDefinition> attributes) {
        this.name = name;
        PathElement path = PathElement.pathElement(name);
        this.registration = ResourceRegistration.of(path);
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(path))
                .addReadOnlyAttributes(attributes)
                .build();
    }

    String getName() {
        return name;
    }

    public static MCPDeploymentRegistrar tool(ParentResourceDescriptionResolver parentResolver) {
        return new MCPDeploymentRegistrar(TOOL, parentResolver, TOOL_ATTRIBUTES);
    }

    public static MCPDeploymentRegistrar prompt(ParentResourceDescriptionResolver parentResolver) {
        return new MCPDeploymentRegistrar(PROMPT, parentResolver, PROMPT_ATTRIBUTES);
    }

    public static MCPDeploymentRegistrar resource(ParentResourceDescriptionResolver parentResolver) {
        return new MCPDeploymentRegistrar(RESOURCE, parentResolver, RESOURCE_ATTRIBUTES);
    }

    public static MCPDeploymentRegistrar resourceTemplate(ParentResourceDescriptionResolver parentResolver) {
        return new MCPDeploymentRegistrar(RESOURCE_TEMPLATE, parentResolver, RESOURCE_ATTRIBUTES);
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDefinition definition = ResourceDefinition.builder(this.registration, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }
}
