/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.chat;

import static org.wildfly.extension.ai.AIAttributeDefinitions.API_KEY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.FREQUENCY_PENALTY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS_RESPONSES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MAX_RETRIES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MAX_TOKEN;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MODEL_NAME;
import static org.wildfly.extension.ai.AIAttributeDefinitions.PRESENCE_PENALTY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.RESPONSE_FORMAT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STREAMING;
import static org.wildfly.extension.ai.AIAttributeDefinitions.TEMPERATURE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.TOP_P;
import static org.wildfly.extension.ai.Capabilities.OPENTELEMETRY_CAPABILITY_NAME;
import static org.wildfly.extension.ai.chat.GithubModelChatLanguageModelProviderRegistrar.CUSTOM_HEADERS;
import static org.wildfly.extension.ai.chat.GithubModelChatLanguageModelProviderRegistrar.ENDPOINT;
import static org.wildfly.extension.ai.chat.GithubModelChatLanguageModelProviderRegistrar.SEED;
import static org.wildfly.extension.ai.chat.GithubModelChatLanguageModelProviderRegistrar.SERVICE_VERSION;
import static org.wildfly.extension.ai.chat.GithubModelChatLanguageModelProviderRegistrar.USER_AGENT_SUFFIX;

import java.util.Map;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.ai.AIAttributeDefinitions;
import org.wildfly.extension.ai.CredentialReferenceUtil;

import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.chat.WildFlyGithubModelChatModelConfig;

import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.ResourceServiceInstaller;

public class GithubModelChatModelProviderServiceConfigurator extends AbstractChatModelProviderServiceConfigurator {

    public GithubModelChatModelProviderServiceConfigurator(ValueRegistry<String, WildFlyChatModelConfig> registry) {
        super(registry);
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String endpoint = ENDPOINT.resolveModelAttribute(context, model).asString();
        Map<String, String> customHeaders = CUSTOM_HEADERS.unwrap(context, model);
        Long connectTimeOut = CONNECT_TIMEOUT.resolveModelAttribute(context, model).asLong();
        Double frequencyPenalty = FREQUENCY_PENALTY.resolveModelAttribute(context, model).asDoubleOrNull();
        ServiceDependency<CredentialSource> credentialReference = model.hasDefined(API_KEY.getName())
                ? ServiceDependency.from(CredentialReference.getCredentialSourceDependency(context, API_KEY, model)) : null;
        String modelName = MODEL_NAME.resolveModelAttribute(context, model).asString();
        Integer maxRetries = MAX_RETRIES.resolveModelAttribute(context, model).asIntOrNull();
        Integer maxToken = MAX_TOKEN.resolveModelAttribute(context, model).asIntOrNull();
        Double presencePenalty = PRESENCE_PENALTY.resolveModelAttribute(context, model).asDoubleOrNull();
        Boolean logRequestsAndResponses = LOG_REQUESTS_RESPONSES.resolveModelAttribute(context, model).asBooleanOrNull();
        boolean isJson = AIAttributeDefinitions.ResponseFormat.isJson(RESPONSE_FORMAT.resolveModelAttribute(context, model).asStringOrNull());
        Long seed = SEED.resolveModelAttribute(context, model).asLongOrNull();
        String serviceVersion = SERVICE_VERSION.resolveModelAttribute(context, model).asStringOrNull();
        Boolean streaming = STREAMING.resolveModelAttribute(context, model).asBooleanOrNull();
        Double temperature = TEMPERATURE.resolveModelAttribute(context, model).asDoubleOrNull();
        Double topP = TOP_P.resolveModelAttribute(context, model).asDoubleOrNull();
        String userAgentSuffix = USER_AGENT_SUFFIX.resolveModelAttribute(context, model).asStringOrNull();
        boolean isObservable= context.getCapabilityServiceSupport().hasCapability(OPENTELEMETRY_CAPABILITY_NAME);
        Supplier<WildFlyChatModelConfig> factory = new Supplier<>() {
            @Override
            public WildFlyChatModelConfig get() {
                return new WildFlyGithubModelChatModelConfig()
                        .customHeaders(customHeaders)
                        .endpoint(endpoint)
                        .frequencyPenalty(frequencyPenalty)
                        .gitHubToken(credentialReference == null ? null : CredentialReferenceUtil.getPassword(credentialReference.get()))
                        .logRequestsAndResponses(logRequestsAndResponses)
                        .maxRetries(maxRetries)
                        .maxTokens(maxToken)
                        .modelName(modelName)
                        .presencePenalty(presencePenalty)
                        .seed(seed)
                        .serviceVersion(serviceVersion)
                        .setJson(isJson)
                        .setObservable(isObservable)
                        .setStreaming(streaming)
                        .temperature(temperature)
                        .timeout(connectTimeOut)
                        .topP(topP)
                        .userAgentSuffix(userAgentSuffix);
            }
        };
        return installService(context.getCurrentAddressValue(), factory, credentialReference);
    }
}
