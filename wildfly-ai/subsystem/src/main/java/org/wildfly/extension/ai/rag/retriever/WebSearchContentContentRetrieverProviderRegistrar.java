/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.rag.retriever;


import static org.wildfly.extension.ai.AIAttributeDefinitions.API_KEY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.BASE_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_RESPONSES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MAX_RETRIES;

import java.util.Collection;
import java.util.List;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
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
public class WebSearchContentContentRetrieverProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition GOOGLE_SEARCH_ENGINE = new ObjectTypeAttributeDefinition.Builder("google",
            API_KEY,
            SimpleAttributeDefinitionBuilder.create(CONNECT_TIMEOUT).setDefaultValue(new ModelNode(60000L)).setStability(Stability.EXPERIMENTAL).build(),
            SimpleAttributeDefinitionBuilder.create("custom-search-id", ModelType.STRING, true).setStability(Stability.EXPERIMENTAL).build(),
            SimpleAttributeDefinitionBuilder.create("include-images", ModelType.BOOLEAN, true).setStability(Stability.EXPERIMENTAL).build(),
            LOG_REQUESTS,
            LOG_RESPONSES,
            MAX_RETRIES,
            SimpleAttributeDefinitionBuilder.create("site-restrict", ModelType.BOOLEAN, true).setStability(Stability.EXPERIMENTAL).build())
                .setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT)
                .setAttributeParser(AttributeParser.OBJECT_PARSER)
                .setAllowExpression(true)
                .addAlternatives("tavily")
                .setRestartAllServices()
                .setStability(Stability.EXPERIMENTAL)
                .build();

    public static final SimpleAttributeDefinition TAVILY_SEARCH_ENGINE = new ObjectTypeAttributeDefinition.Builder("tavily",
            API_KEY,
            BASE_URL,
            CONNECT_TIMEOUT,
            StringListAttributeDefinition.Builder.of("exclude-domains")
                .setRequired(false)
                .setMinSize(0)
                .setAllowExpression(true)
                .setRestartAllServices()
                .setStability(Stability.EXPERIMENTAL)
                .build(),
            SimpleAttributeDefinitionBuilder.create("include-answer", ModelType.BOOLEAN, true).setStability(Stability.EXPERIMENTAL).build(),
            StringListAttributeDefinition.Builder.of("include-domains")
                .setRequired(false)
                .setMinSize(0)
                .setAllowExpression(true)
                .setRestartAllServices()
                .setStability(Stability.EXPERIMENTAL)
                .build(),
            SimpleAttributeDefinitionBuilder.create("include-raw-content", ModelType.BOOLEAN, true).setStability(Stability.EXPERIMENTAL).build(),
            SimpleAttributeDefinitionBuilder.create("search-depth", ModelType.STRING, true).setAllowedValues("basic", "advanced").setStability(Stability.EXPERIMENTAL).build())
                .setAllowExpression(true)
                .addAlternatives("google")
                .setRestartAllServices()
                .setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT)
                .setAttributeParser(AttributeParser.OBJECT_PARSER)
                .setStability(Stability.EXPERIMENTAL)
                .build();

    public static final SimpleAttributeDefinition MAX_RESULTS = SimpleAttributeDefinitionBuilder.create("max-results", ModelType.INT, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setStability(Stability.EXPERIMENTAL)
            .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(GOOGLE_SEARCH_ENGINE, MAX_RESULTS, TAVILY_SEARCH_ENGINE);

    private final ResourceDescriptor descriptor;
    static final String NAME = "web-search-content-retriever";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);

    public WebSearchContentContentRetrieverProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(Capabilities.CONTENT_RETRIEVER_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new WebSearchContentRetrieverProviderServiceConfigurator()))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext mrrc) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("dev.langchain4j.web-search-engines"));
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }

}
