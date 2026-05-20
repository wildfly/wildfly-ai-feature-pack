/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.rag.retriever;

import static org.wildfly.extension.ai.Capabilities.EMBEDDING_MODEL_PROVIDER_DESCRIPTOR;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_STORE_PROVIDER_DESCRIPTOR;

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
import org.wildfly.extension.ai.Capabilities;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

/**
 *
 * @author Emmanuel Hugonnet (c) 2024 Red Hat, Inc.
 */
public class EmbeddingStoreContentRetrieverProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition EMBEDDING_STORE = SimpleAttributeDefinitionBuilder.create("embedding-store", ModelType.STRING, false)
            .setAllowExpression(true)
            .setCapabilityReference(EMBEDDING_STORE_PROVIDER_DESCRIPTOR.getName())
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition EMBEDDING_MODEL = SimpleAttributeDefinitionBuilder.create("embedding-model", ModelType.STRING, false)
            .setAllowExpression(true)
            .setCapabilityReference(EMBEDDING_MODEL_PROVIDER_DESCRIPTOR.getName())
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MAX_RESULTS = SimpleAttributeDefinitionBuilder.create("max-results", ModelType.INT, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MIN_SCORE = SimpleAttributeDefinitionBuilder.create("min-score", ModelType.DOUBLE, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
//    public static final SimpleAttributeDefinition FILTER = SimpleAttributeDefinitionBuilder.create("filter", ModelType.STRING, true)
//            .setAllowExpression(true)
//            .setRestartAllServices()
//            .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(EMBEDDING_MODEL, EMBEDDING_STORE, /*FILTER,*/ MAX_RESULTS, MIN_SCORE);

    private final ResourceDescriptor descriptor;
    static final String NAME = "embedding-store-content-retriever";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public EmbeddingStoreContentRetrieverProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(Capabilities.CONTENT_RETRIEVER_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new EmbeddingStoreContentRetrieverProviderServiceConfigurator()))
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
