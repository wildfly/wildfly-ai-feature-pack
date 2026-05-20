/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.chat;

import static org.wildfly.extension.ai.AIAttributeDefinitions.BASE_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.EXECUTOR_SERVICE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_RESPONSES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MAX_RETRIES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MODEL_NAME;
import static org.wildfly.extension.ai.AIAttributeDefinitions.SEED;
import static org.wildfly.extension.ai.AIAttributeDefinitions.TEMPERATURE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.TOP_P;
import static org.wildfly.extension.ai.Capabilities.CHAT_MODEL_PROVIDER_CAPABILITY;

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

import static org.wildfly.extension.ai.AIAttributeDefinitions.RESPONSE_FORMAT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STOP_SEQUENCES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STREAMING;

import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.service.capture.ValueExecutorRegistry;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class OllamaChatLanguageModelProviderRegistrar implements ChildResourceDefinitionRegistrar {

    public static final SimpleAttributeDefinition NUM_PREDICT = new SimpleAttributeDefinitionBuilder("num-predict", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition REPEAT_PENALTY = new SimpleAttributeDefinitionBuilder("repeat-penalty", ModelType.DOUBLE, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition TOP_K = new SimpleAttributeDefinitionBuilder("top-k", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();

    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(BASE_URL, CONNECT_TIMEOUT,
            EXECUTOR_SERVICE, LOG_REQUESTS, LOG_RESPONSES, MAX_RETRIES, MODEL_NAME, NUM_PREDICT, REPEAT_PENALTY,
            RESPONSE_FORMAT, SEED, STOP_SEQUENCES, STREAMING, TEMPERATURE, TOP_K, TOP_P);

    private final ResourceDescriptor descriptor;
    static final String NAME = "ollama-chat-model";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);
    private final ValueExecutorRegistry<String, WildFlyChatModelConfig> registry = ValueExecutorRegistry.newInstance();

    public OllamaChatLanguageModelProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(CHAT_MODEL_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new OllamaChatModelProviderServiceConfigurator(registry)))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        ChatModelConnectionCheckerOperationHandler.register(resourceRegistration, descriptor, registry);
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("dev.langchain4j.ollama"));
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }
}
