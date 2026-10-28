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
import static org.wildfly.extension.ai.Capabilities.MANAGED_EXECUTOR_CAPABILITY_NAME;
import static org.wildfly.extension.ai.Capabilities.OPENTELEMETRY_CAPABILITY_NAME;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.ALLOWED_CODE_EXECUTION;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.CIVIC_INTEGRITY;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.DANGEROUS_CONTENT;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.ENABLE_ENHANCED_CIVIC_ANSWERS;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.HARASSMENT;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.HATE_SPEECH;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.INCLUDE_CODE_EXECUTION_OUTPUT;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.INCLUDE_THOUGHTS;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.LOG_PROBS;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.MAX_OUTPUT_TOKEN;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.RESPONSE_LOG_PROBS;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.RETURN_THINKING;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.SEXUALLY_EXPLICIT;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.THINKING_BUDGET;
import static org.wildfly.extension.ai.chat.GeminiChatLanguageModelProviderRegistrar.TOP_K;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.ee.concurrent.adapter.ManagedExecutorServiceAdapter;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.ai.AIAttributeDefinitions;
import org.wildfly.extension.ai.CredentialReferenceUtil;

import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.chat.WildFlyGeminiChatModelConfig;

import org.wildfly.service.capture.ValueRegistry;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Configures an aggregate ChatModel provider service.
 */
public class GeminiChatModelProviderServiceConfigurator extends AbstractChatModelProviderServiceConfigurator {

    private static final Map<String, String> HARM_CATEGORIES = Map.of(
            HATE_SPEECH.getName(), "HARM_CATEGORY_HATE_SPEECH",
            SEXUALLY_EXPLICIT.getName(), "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            DANGEROUS_CONTENT.getName(), "HARM_CATEGORY_DANGEROUS_CONTENT",
            HARASSMENT.getName(), "HARM_CATEGORY_HARASSMENT",
            CIVIC_INTEGRITY.getName(), "HARM_CATEGORY_CIVIC_INTEGRITY"
    );

    public GeminiChatModelProviderServiceConfigurator(ValueRegistry<String, WildFlyChatModelConfig> registry) {
        super(registry);
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        Long connectTimeOut = CONNECT_TIMEOUT.resolveModelAttribute(context, model).asLong();
        ServiceDependency<CredentialSource> credentialReference = model.hasDefined(API_KEY.getName())
                ? ServiceDependency.from(CredentialReference.getCredentialSourceDependency(context, API_KEY, model)) : null;
        Boolean allowCodeExecution = ALLOWED_CODE_EXECUTION.resolveModelAttribute(context, model).asBooleanOrNull();
        Boolean enableEnhancedCivicAnswers = ENABLE_ENHANCED_CIVIC_ANSWERS.resolveModelAttribute(context, model).asBooleanOrNull();
        Double frequencyPenalty = FREQUENCY_PENALTY.resolveModelAttribute(context, model).asDoubleOrNull();
        Boolean includeCodeExecutionOutput = INCLUDE_CODE_EXECUTION_OUTPUT.resolveModelAttribute(context, model).asBooleanOrNull();
        Boolean includeThoughts = INCLUDE_THOUGHTS.resolveModelAttribute(context, model).asBooleanOrNull();
        Integer logProbs = LOG_PROBS.resolveModelAttribute(context, model).asIntOrNull();
        Boolean logRequests = LOG_REQUESTS.resolveModelAttribute(context, model).asBooleanOrNull();
        Boolean logResponses = LOG_RESPONSES.resolveModelAttribute(context, model).asBooleanOrNull();
        Integer maxOutputTokens = MAX_OUTPUT_TOKEN.resolveModelAttribute(context, model).asIntOrNull();
        String modelName = MODEL_NAME.resolveModelAttribute(context, model).asString();
        Double presencePenalty = PRESENCE_PENALTY.resolveModelAttribute(context, model).asDoubleOrNull();
        boolean isJson = AIAttributeDefinitions.ResponseFormat.isJson(RESPONSE_FORMAT.resolveModelAttribute(context, model).asStringOrNull());
        Boolean responseLogprobs = RESPONSE_LOG_PROBS.resolveModelAttribute(context, model).asBooleanOrNull();
        Boolean returnThinking = RETURN_THINKING.resolveModelAttribute(context, model).asBooleanOrNull();
        Integer seed = SEED.resolveModelAttribute(context, model).asIntOrNull();
        List<String> stopSequences = STOP_SEQUENCES.unwrap(context, model);
        Boolean streaming = STREAMING.resolveModelAttribute(context, model).asBooleanOrNull();
        Double temperature = TEMPERATURE.resolveModelAttribute(context, model).asDoubleOrNull();
        Integer thinkingBudget = THINKING_BUDGET.resolveModelAttribute(context, model).asIntOrNull();
        Integer topK = TOP_K.resolveModelAttribute(context, model).asIntOrNull();
        Double topP = TOP_P.resolveModelAttribute(context, model).asDoubleOrNull();
        boolean isObservable = context.getCapabilityServiceSupport().hasCapability(OPENTELEMETRY_CAPABILITY_NAME);
        final String executorServiceName= EXECUTOR_SERVICE.resolveModelAttribute(context, model).asString();
        final ServiceDependency<ManagedExecutorServiceAdapter> executorAdapter = ServiceDependency.on(MANAGED_EXECUTOR_CAPABILITY_NAME, ManagedExecutorServiceAdapter.class, executorServiceName);
        Map<String, String> safetySettingsConfig = safetySettingConfig(context, model);
        Supplier<WildFlyChatModelConfig> factory = new Supplier<>() {
            @Override
            public WildFlyChatModelConfig get() {
                return new WildFlyGeminiChatModelConfig()
                        .allowCodeExecution(allowCodeExecution)
                        .apiKey(credentialReference == null ? "" : CredentialReferenceUtil.getPassword(credentialReference.get()))
                        .enableEnhancedCivicAnswers(enableEnhancedCivicAnswers)
                        .frequencyPenalty(frequencyPenalty)
                        .includeCodeExecutionOutput(includeCodeExecutionOutput)
                        .includeThoughts(includeThoughts)
                        .httpClientBuilder(new JdkHttpClientBuilderFactory().create().httpClientBuilder(HttpClient.newBuilder().executor(executorAdapter.get())))
                        .logprobs(logProbs)
                        .logRequests(logRequests)
                        .logResponses(logResponses)
                        .maxOutputTokens(maxOutputTokens)
                        .modelName(modelName)
                        .presencePenalty(presencePenalty)
                        .responseLogprobs(responseLogprobs)
                        .returnThinking(returnThinking)
                        .seed(seed)
                        .setJson(isJson)
                        .setObservable(isObservable)
                        .safetySettings(safetySettingsConfig)
                        .setStreaming(streaming)
                        .stopSequences(stopSequences)
                        .thinkingBudget(thinkingBudget)
                        .temperature(temperature)
                        .timeout(connectTimeOut)
                        .topK(topK)
                        .topP(topP);
            }
        };
        return installService(context.getCurrentAddressValue(), factory, isObservable, executorAdapter, credentialReference);
    }

    private Map<String, String> safetySettingConfig(OperationContext context, ModelNode model) throws OperationFailedException {
        Map<String, String> safetySettings = new HashMap<>();
        setSafetySettingConfig(safetySettings, HATE_SPEECH, context, model);
        setSafetySettingConfig(safetySettings, SEXUALLY_EXPLICIT, context, model);
        setSafetySettingConfig(safetySettings, DANGEROUS_CONTENT, context, model);
        setSafetySettingConfig(safetySettings, HARASSMENT, context, model);
        setSafetySettingConfig(safetySettings, CIVIC_INTEGRITY, context, model);
        return safetySettings;
    }

    private void setSafetySettingConfig(Map<String, String> safetySettings, AttributeDefinition att, OperationContext context, ModelNode model) throws OperationFailedException {
        String value = att.resolveModelAttribute(context, model).asStringOrNull();
        if(value != null) {
            safetySettings.put(HARM_CATEGORIES.get(att.getName()), value);
        }
    }
}
