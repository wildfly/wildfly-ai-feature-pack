/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.chat;

import static org.jboss.dmr.ModelType.STRING;
import static org.wildfly.extension.ai.AIAttributeDefinitions.USER_MESSAGE;

import java.util.Collections;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.ai.AILogger;
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.service.capture.FunctionExecutor;
import org.wildfly.service.capture.ValueExecutorRegistry;
import org.wildfly.subsystem.resource.ResourceDescriptor;

/**
 *
 * @author Emmanuel Hugonnet (c) 2024 Red Hat, Inc.
 */
public class ChatModelConnectionCheckerOperationHandler implements OperationStepHandler {
    private static final String OPERATION_NAME = "chat";

    public static void register(final ManagementResourceRegistration registration, final ResourceDescriptor descriptor, ValueExecutorRegistry<String, WildFlyChatModelConfig> registry) {
        registration.registerOperationHandler(SimpleOperationDefinitionBuilder.of(OPERATION_NAME, descriptor.getResourceDescriptionResolver())
                .setParameters(USER_MESSAGE)
                .setReplyType(STRING)
                .setRuntimeOnly()
                .setReadOnly()
                .setStability(Stability.EXPERIMENTAL)
                .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
                .build(), new ChatModelConnectionCheckerOperationHandler(registry));
    }

    private final ValueExecutorRegistry<String, WildFlyChatModelConfig> registry;

    ChatModelConnectionCheckerOperationHandler(ValueExecutorRegistry<String, WildFlyChatModelConfig> registry) {
        this.registry = registry;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        String userMessage = USER_MESSAGE.resolveModelAttribute(context, operation).asString();
        FunctionExecutor<WildFlyChatModelConfig> executor = registry.getExecutor(context.getCurrentAddressValue());
        if(executor == null) {
            throw AILogger.ROOT_LOGGER.chatLanguageModelServiceUnavailable(context.getCurrentAddressValue());
        }
        ModelNode answer = executor.execute((WildFlyChatModelConfig chatLanguageModelConfig) -> {
            AILogger.ROOT_LOGGER.debugf("About to execute a chat call to the LLM with the following user message: %s", userMessage);
            String response = (chatLanguageModelConfig.createLanguageModel(Collections.emptyList())).chat(userMessage);
            AILogger.ROOT_LOGGER.debugf("This is the answer I got: %s", response);
            return new ModelNode(response);
        });
        context.getResult().set(answer);
    }

}
