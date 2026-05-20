/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.embedding.store;

import static org.wildfly.extension.ai.AIAttributeDefinitions.BASE_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_RESPONSES;
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
import org.jboss.as.controller.operations.validation.EnumValidator;
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

public class ChromaEmbeddingStoreProviderRegistrar implements ChildResourceDefinitionRegistrar {

    protected static final SimpleAttributeDefinition API_VERSION = new SimpleAttributeDefinitionBuilder("api-version", ModelType.STRING, false)
            .setValidator(EnumValidator.create(ChromaAPIVersion.class))
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    protected static final SimpleAttributeDefinition COLLECTION_NAME
            = new SimpleAttributeDefinitionBuilder("collection-name", ModelType.STRING, true)
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    protected static final SimpleAttributeDefinition DATABASE_NAME
            = new SimpleAttributeDefinitionBuilder("database-name", ModelType.STRING, true)
                    .setDefaultValue(new ModelNode("default"))
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();
    protected static final SimpleAttributeDefinition TENANT_NAME
            = new SimpleAttributeDefinitionBuilder("tenant-name", ModelType.STRING, true)
                    .setDefaultValue(new ModelNode("default"))
                    .setAllowExpression(true)
                    .setStability(Stability.EXPERIMENTAL)
                    .build();

    private final ResourceDescriptor descriptor;
    static final String NAME = "chroma-embedding-store";
    public static final PathElement PATH = PathElement.pathElement(NAME);

    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(API_VERSION, BASE_URL, COLLECTION_NAME, CONNECT_TIMEOUT, DATABASE_NAME, LOG_REQUESTS, LOG_RESPONSES, TENANT_NAME);

    public ChromaEmbeddingStoreProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(EMBEDDING_STORE_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new ChromaEmbeddingStoreProviderServiceConfigurator()))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext mrrc) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("dev.langchain4j.chroma"));
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }

    public enum ChromaAPIVersion {
        V1, V2;
    }
}
