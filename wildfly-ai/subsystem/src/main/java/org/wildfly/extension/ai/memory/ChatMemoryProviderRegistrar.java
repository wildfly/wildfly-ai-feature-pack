/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.memory;

import static org.wildfly.extension.ai.Capabilities.CHAT_MEMORY_PROVIDER_CAPABILITY;

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

import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class ChatMemoryProviderRegistrar implements ChildResourceDefinitionRegistrar {
    public static final SimpleAttributeDefinition USE_HTTP_SESSION = new SimpleAttributeDefinitionBuilder("use-http-session", ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.TRUE)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition SIZE = new SimpleAttributeDefinitionBuilder("size", ModelType.INT, false)
            .setAllowExpression(true)
            .setValidator(IntRangeValidator.POSITIVE)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder("type", ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(EnumValidator.create(org.wildfly.extension.ai.injection.memory.WildFlyChatMemoryProviderConfig.ChatMemoryType.class))
            .setStability(Stability.EXPERIMENTAL)
            .build();


    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(SIZE, TYPE, USE_HTTP_SESSION);

    private final ResourceDescriptor descriptor;
    static final String NAME = "chat-memory";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public ChatMemoryProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(CHAT_MEMORY_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new ChatMemoryProviderServiceConfigurator()))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }
}
