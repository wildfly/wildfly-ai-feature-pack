/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.chat;

import static org.wildfly.extension.ai.AIAttributeDefinitions.API_KEY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.BASE_URL;
import static org.wildfly.extension.ai.AIAttributeDefinitions.CONNECT_TIMEOUT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.EXECUTOR_SERVICE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.FREQUENCY_PENALTY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_REQUESTS;
import static org.wildfly.extension.ai.AIAttributeDefinitions.LOG_RESPONSES;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MAX_TOKEN;
import static org.wildfly.extension.ai.AIAttributeDefinitions.MODEL_NAME;
import static org.wildfly.extension.ai.AIAttributeDefinitions.PRESENCE_PENALTY;
import static org.wildfly.extension.ai.AIAttributeDefinitions.RESPONSE_FORMAT;
import static org.wildfly.extension.ai.AIAttributeDefinitions.SEED;
import static org.wildfly.extension.ai.AIAttributeDefinitions.STREAMING;
import static org.wildfly.extension.ai.AIAttributeDefinitions.TEMPERATURE;
import static org.wildfly.extension.ai.AIAttributeDefinitions.TOP_P;
import static org.wildfly.extension.ai.Capabilities.MANAGED_EXECUTOR_CAPABILITY_NAME;
import static org.wildfly.extension.ai.Capabilities.OPENTELEMETRY_CAPABILITY_NAME;
import static org.wildfly.extension.ai.chat.OpenAIChatLanguageModelProviderRegistrar.ORGANIZATION_ID;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory;
import java.net.http.HttpClient;
import java.util.function.Supplier;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.ee.concurrent.adapter.ManagedExecutorServiceAdapter;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.ai.AIAttributeDefinitions;
import org.wildfly.extension.ai.CredentialReferenceUtil;

import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.chat.WildFlyOpenAiChatModelConfig;

import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Configures an aggregate ChatModel provider service.
 */
public class OpenAIChatModelProviderServiceConfigurator extends AbstractChatModelProviderServiceConfigurator {

    public OpenAIChatModelProviderServiceConfigurator(ValueRegistry<String, WildFlyChatModelConfig> registry) {
        super(registry);
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String baseUrl = BASE_URL.resolveModelAttribute(context, model).asString();
        Long connectTimeOut = CONNECT_TIMEOUT.resolveModelAttribute(context, model).asLong();
        Double frequencyPenalty = FREQUENCY_PENALTY.resolveModelAttribute(context, model).asDoubleOrNull();
        ServiceDependency<CredentialSource> credentialReference = model.hasDefined(API_KEY.getName())
                ? ServiceDependency.from(CredentialReference.getCredentialSourceDependency(context, API_KEY, model)) : null;
        String organizationId = ORGANIZATION_ID.resolveModelAttribute(context, model).asString();
        String modelName = MODEL_NAME.resolveModelAttribute(context, model).asString();
        Integer maxToken = MAX_TOKEN.resolveModelAttribute(context, model).asIntOrNull();
        Double presencePenalty = PRESENCE_PENALTY.resolveModelAttribute(context, model).asDoubleOrNull();
        Boolean logRequests = LOG_REQUESTS.resolveModelAttribute(context, model).asBooleanOrNull();
        Boolean logResponses = LOG_RESPONSES.resolveModelAttribute(context, model).asBooleanOrNull();
        boolean isJson = AIAttributeDefinitions.ResponseFormat.isJson(RESPONSE_FORMAT.resolveModelAttribute(context, model).asStringOrNull());
        Integer seed = SEED.resolveModelAttribute(context, model).asIntOrNull();
        Boolean streaming = STREAMING.resolveModelAttribute(context, model).asBooleanOrNull();
        Double temperature = TEMPERATURE.resolveModelAttribute(context, model).asDoubleOrNull();
        Double topP = TOP_P.resolveModelAttribute(context, model).asDoubleOrNull();
        boolean isObservable= context.getCapabilityServiceSupport().hasCapability(OPENTELEMETRY_CAPABILITY_NAME);
        final String executorServiceName= EXECUTOR_SERVICE.resolveModelAttribute(context, model).asString();
        final ServiceDependency<ManagedExecutorServiceAdapter> executorAdapter = ServiceDependency.on(MANAGED_EXECUTOR_CAPABILITY_NAME, ManagedExecutorServiceAdapter.class, executorServiceName);
        Supplier<WildFlyChatModelConfig> factory = new Supplier<>() {
            @Override
            public WildFlyChatModelConfig get() {
                return new WildFlyOpenAiChatModelConfig()
                        .apiKey(credentialReference == null ? null : CredentialReferenceUtil.getPassword(credentialReference.get()))
                        .baseUrl(baseUrl)
                        .frequencyPenalty(frequencyPenalty)
                        .httpClientBuilder(new JdkHttpClientBuilderFactory().create().httpClientBuilder(HttpClient.newBuilder().executor(executorAdapter.get())))
                        .logRequests(logRequests)
                        .logResponses(logResponses)
                        .maxTokens(maxToken)
                        .modelName(modelName)
                        .organizationId(organizationId)
                        .presencePenalty(presencePenalty)
                        .seed(seed)
                        .setJson(isJson)
                        .setObservable(isObservable)
                        .setStreaming(streaming)
                        .temperature(temperature)
                        .timeout(connectTimeOut)
                        .topP(topP);
            }
        };
        return installService(context.getCurrentAddressValue(), factory, isObservable, executorAdapter, credentialReference);
    }
}
