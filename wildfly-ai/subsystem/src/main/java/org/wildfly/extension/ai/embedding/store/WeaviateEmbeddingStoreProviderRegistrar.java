/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.embedding.store;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING;
import static org.wildfly.extension.ai.AIAttributeDefinitions.SSL_ENABLED;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_STORE_PROVIDER_CAPABILITY;
import static org.wildfly.extension.ai.Capabilities.OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME;

import java.util.Collection;
import java.util.List;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class WeaviateEmbeddingStoreProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition AVOID_DUPS = SimpleAttributeDefinitionBuilder.create("avoid-dups", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition CONSISTENCY_LEVEL = SimpleAttributeDefinitionBuilder.create("consistency-level", ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("ALL"))
            .setAllowedValues("ONE", "QUORUM", "ALL")
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition OBJECT_CLASS = SimpleAttributeDefinitionBuilder.create("object-class", ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition STORE_BINDING = SimpleAttributeDefinitionBuilder.create(SOCKET_BINDING, ModelType.STRING, false)
            .setAllowExpression(true)
            .setCapabilityReference(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final StringListAttributeDefinition METADATA = StringListAttributeDefinition.Builder.of("metadata")
            .setRequired(false)
            .setMinSize(0)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(AVOID_DUPS, CONSISTENCY_LEVEL, METADATA, OBJECT_CLASS, SSL_ENABLED, STORE_BINDING);

    private final ResourceDescriptor descriptor;
    static final String NAME = "weaviate-embedding-store";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public WeaviateEmbeddingStoreProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(EMBEDDING_STORE_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new WeaviateEmbeddingStoreProviderServiceConfigurator()))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("dev.langchain4j.weaviate"));
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }

}
