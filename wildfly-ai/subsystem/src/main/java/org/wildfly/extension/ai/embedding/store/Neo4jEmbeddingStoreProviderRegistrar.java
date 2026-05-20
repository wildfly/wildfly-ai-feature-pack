/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.embedding.store;

import static org.wildfly.extension.ai.AIAttributeDefinitions.BOLT_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CREDENTIAL_REFERENCE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.USERNAME;
import static org.wildfly.extension.ai.Capabilities.EMBEDDING_STORE_PROVIDER_CAPABILITY;

import java.util.Collection;
import java.util.List;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class Neo4jEmbeddingStoreProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition DATABASE_NAME
            = new SimpleAttributeDefinitionBuilder("database-name", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition DIMENSION
            = new SimpleAttributeDefinitionBuilder("dimension", ModelType.INT, false)
                    .setAllowExpression(true)
                    .setValidator(IntRangeValidator.POSITIVE)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition EMBEDDING_PROPERTY
            = new SimpleAttributeDefinitionBuilder("embedding-property", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition ID_PROPERTY
            = new SimpleAttributeDefinitionBuilder("id-property", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition INDEX_NAME
            = new SimpleAttributeDefinitionBuilder("index-name", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition LABEL
            = new SimpleAttributeDefinitionBuilder("label", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition METADATA_PREFIX
            = new SimpleAttributeDefinitionBuilder("metadata-prefix", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition RETRIEVAL_QUERY
            = new SimpleAttributeDefinitionBuilder("retrieval-query", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    public static final SimpleAttributeDefinition TEXT_PROPERTY
            = new SimpleAttributeDefinitionBuilder("text-property", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(BOLT_URL, CREDENTIAL_REFERENCE, DATABASE_NAME,
            DIMENSION, EMBEDDING_PROPERTY, ID_PROPERTY, INDEX_NAME, LABEL, METADATA_PREFIX, RETRIEVAL_QUERY, TEXT_PROPERTY,
            USERNAME);

    private final ResourceDescriptor descriptor;
    static final String NAME = "neo4j-embedding-store";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public Neo4jEmbeddingStoreProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(EMBEDDING_STORE_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new Neo4jEmbeddingStoreProviderServiceConfigurator()))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext mrrc) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("dev.langchain4j.neo4j"));
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }

}
