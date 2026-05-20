/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.chat;

import static org.wildfly.extension.ai.AIAttributeDefinitions.API_KEY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.EXECUTOR_SERVICE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.FREQUENCY_PENALTY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_RESPONSES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MODEL_NAME;
import static org.wildfly.extension.ai.AIAttributeDefinitions.PRESENCE_PENALTY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.RESPONSE_FORMAT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.SEED;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STOP_SEQUENCES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STREAMING;
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
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.service.capture.ValueExecutorRegistry;
import org.wildfly.subsystem.resource.ChildResourceDefinitionRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrar;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

public class GeminiChatLanguageModelProviderRegistrar implements ChildResourceDefinitionRegistrar {

    private static final String[] THRESHOLDS = new String[]{"HARM_BLOCK_THRESHOLD_UNSPECIFIED", "BLOCK_LOW_AND_ABOVE", "BLOCK_MEDIUM_AND_ABOVE", "BLOCK_ONLY_HIGH", "BLOCK_NONE"};
    public static final SimpleAttributeDefinition ALLOWED_CODE_EXECUTION = new SimpleAttributeDefinitionBuilder("allowed-code-execution", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition CIVIC_INTEGRITY = new SimpleAttributeDefinitionBuilder("civic-integrity", ModelType.STRING, true)
            .setAllowedValues(THRESHOLDS)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition DANGEROUS_CONTENT = new SimpleAttributeDefinitionBuilder("dangerous-content", ModelType.STRING, true)
            .setAllowedValues(THRESHOLDS)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition ENABLE_ENHANCED_CIVIC_ANSWERS = new SimpleAttributeDefinitionBuilder("enable-enhanced-civic-answers", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition HARASSMENT = new SimpleAttributeDefinitionBuilder("harassment", ModelType.STRING, true)
            .setAllowedValues(THRESHOLDS)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition HATE_SPEECH = new SimpleAttributeDefinitionBuilder("hate-speech", ModelType.STRING, true)
            .setAllowedValues(THRESHOLDS)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition INCLUDE_CODE_EXECUTION_OUTPUT = new SimpleAttributeDefinitionBuilder("include-code-execution-output", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition INCLUDE_THOUGHTS = new SimpleAttributeDefinitionBuilder("include-thoughts", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition LOG_PROBS = new SimpleAttributeDefinitionBuilder("log-probs", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition MAX_OUTPUT_TOKEN = new SimpleAttributeDefinitionBuilder("max-output-token", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition RESPONSE_LOG_PROBS = new SimpleAttributeDefinitionBuilder("response-log-probs", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition RETURN_THINKING = new SimpleAttributeDefinitionBuilder("return-thinking", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition SEXUALLY_EXPLICIT = new SimpleAttributeDefinitionBuilder("sexually-explicit", ModelType.STRING, true)
            .setAllowedValues(THRESHOLDS)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition THINKING_BUDGET = new SimpleAttributeDefinitionBuilder("thinking-budget", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();
    public static final SimpleAttributeDefinition TOP_K = new SimpleAttributeDefinitionBuilder("top-k", ModelType.INT, true)
            .setAllowExpression(true)
            .setStability(Stability.EXPERIMENTAL)
            .build();


    public static final Collection<AttributeDefinition> ATTRIBUTES = List.of(ALLOWED_CODE_EXECUTION, API_KEY,
            CIVIC_INTEGRITY, CONNECT_TIMEOUT, DANGEROUS_CONTENT, ENABLE_ENHANCED_CIVIC_ANSWERS, EXECUTOR_SERVICE, FREQUENCY_PENALTY,
            HARASSMENT, HATE_SPEECH, INCLUDE_CODE_EXECUTION_OUTPUT, INCLUDE_THOUGHTS, LOG_PROBS, LOG_REQUESTS, LOG_RESPONSES,
            MAX_OUTPUT_TOKEN, MODEL_NAME, PRESENCE_PENALTY, RESPONSE_FORMAT, RESPONSE_LOG_PROBS, RETURN_THINKING, SEED,
            SEXUALLY_EXPLICIT, STOP_SEQUENCES, STREAMING, TEMPERATURE, THINKING_BUDGET, TOP_K, TOP_P);

    private final ResourceDescriptor descriptor;
    static final String NAME = "gemini-chat-model";
    public static final PathElement PATH = PathElement.pathElement(NAME);
    public static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PATH);
    private final ValueExecutorRegistry<String, WildFlyChatModelConfig> registry = ValueExecutorRegistry.newInstance();

    public GeminiChatLanguageModelProviderRegistrar(ParentResourceDescriptionResolver parentResolver) {
        this.descriptor = ResourceDescriptor.builder(parentResolver.createChildResolver(PATH))
                .addCapability(CHAT_MODEL_PROVIDER_CAPABILITY)
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new GeminiChatModelProviderServiceConfigurator(registry)))
                .build();
    }

    @Override
    public ManagementResourceRegistration register(ManagementResourceRegistration parent, ManagementResourceRegistrationContext context) {
        ResourceDefinition definition = ResourceDefinition.builder(REGISTRATION, this.descriptor.getResourceDescriptionResolver()).build();
        ManagementResourceRegistration resourceRegistration = parent.registerSubModel(definition);
        ChatModelConnectionCheckerOperationHandler.register(resourceRegistration, descriptor, registry);
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("dev.langchain4j.gemini"));
        ManagementResourceRegistrar.of(this.descriptor).register(resourceRegistration);
        return resourceRegistration;
    }
}
