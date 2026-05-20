/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.rag.retriever;


import static org.wildfly.extension.ai.AIAttributeDefinitions.BOLT_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CREDENTIAL_REFERENCE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.USERNAME;
import static org.wildfly.extension.ai.Capabilities.CHAT_MODEL_PROVIDER_DESCRIPTOR;

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
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;
import org.wildfly.extension.ai.Capabilities;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class Neo4JContentRetrieverProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition CHAT_LANGUAGE_MODEL = SimpleAttributeDefinitionBuilder.create("chat-language-model", ModelType.STRING, false)
            .setAllowExpression(true)
            .setCapabilityReference(CHAT_MODEL_PROVIDER_DESCRIPTOR.getName())
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition PROMPT_TEMPLATE = SimpleAttributeDefinitionBuilder.create("prompt-template", ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(BOLT_URL, CHAT_LANGUAGE_MODEL, CREDENTIAL_REFERENCE, USERNAME, PROMPT_TEMPLATE);

    private final ResourceDescriptor descriptor;
    static final String NAME = "neo4j-content-retriever";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public Neo4JContentRetrieverProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(Capabilities.CONTENT_RETRIEVER_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new Neo4JContentRetrieverProviderServiceConfigurator()))
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
