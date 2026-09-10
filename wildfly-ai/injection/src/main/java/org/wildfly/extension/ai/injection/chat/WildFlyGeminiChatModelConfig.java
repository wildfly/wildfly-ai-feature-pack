/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.injection.chat;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GeminiHarmBlockThreshold;
import dev.langchain4j.model.googleai.GeminiHarmCategory;
import dev.langchain4j.model.googleai.GeminiSafetySetting;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.wildfly.extension.ai.injection.AILogger;

public class WildFlyGeminiChatModelConfig implements WildFlyChatModelConfig {

    private List<GeminiSafetySetting> safetySettings;
    private List<String> stopSequences;
    private Boolean allowCodeExecution;
    private String key;
    private Boolean includeCodeExecutionOutput;
    private Double frequencyPenalty;
    private Boolean logRequests;
    private Boolean logResponses;
    private Integer maxOutputTokens;
    private String modelName;
    private Double temperature;
    private Duration connectTimeOut;
    private Integer topK;
    private Double topP;
    private Integer seed;
    private Double presencePenalty;
    private Boolean includeThoughts;
    private Integer thinkingBudget;
    private Boolean returnThinking;
    private Boolean responseLogprobs;
    private Boolean enableEnhancedCivicAnswers;
    private Integer logprobs;;
    private HttpClientBuilder httpClientBuilder;
    private boolean isJson;
    private boolean streaming;
    private boolean observable;
    private Object instance = null;

    @Override
    public ChatModel createLanguageModel(List<ChatModelListener> listeners) {
        if (instance == null) {
            GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                    .allowCodeExecution(allowCodeExecution)
                    .apiKey(key)
                    .enableEnhancedCivicAnswers(enableEnhancedCivicAnswers)
                    .frequencyPenalty(frequencyPenalty)
                    .includeCodeExecutionOutput(includeCodeExecutionOutput)
                    .logprobs(logprobs)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .maxOutputTokens(maxOutputTokens)
                    .maxRetries(5)
                    .modelName(modelName)
                    .presencePenalty(presencePenalty)
                    .responseLogprobs(responseLogprobs)
                    .returnThinking(returnThinking)
                    .safetySettings(safetySettings)
                    .seed(seed)
                    .stopSequences(stopSequences)
                    .temperature(temperature)
                    .thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(includeThoughts)
                            .thinkingBudget(thinkingBudget)
                            .build())
                    .timeout(connectTimeOut)
                    .topK(topK)
                    .topP(topP);
            if (isJson) {
                builder.responseFormat(ResponseFormat.JSON);
            }
            if(httpClientBuilder != null) {
                builder.httpClientBuilder(httpClientBuilder);
            }
            if (listeners != null && !listeners.isEmpty()) {
                builder.listeners(listeners);
            }
            instance = builder.build();
        }
        return (ChatModel) instance;
    }

    @Override
    public StreamingChatModel createStreamingLanguageModel(List<ChatModelListener> listeners) {
        if (instance == null) {
            GoogleAiGeminiStreamingChatModel.GoogleAiGeminiStreamingChatModelBuilder builder = GoogleAiGeminiStreamingChatModel.builder()
                    .allowCodeExecution(allowCodeExecution)
                    .apiKey(key)
                    .enableEnhancedCivicAnswers(enableEnhancedCivicAnswers)
                    .frequencyPenalty(frequencyPenalty)
                    .includeCodeExecutionOutput(includeCodeExecutionOutput)
                    .logprobs(logprobs)
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .maxOutputTokens(maxOutputTokens)
                    .modelName(modelName)
                    .presencePenalty(presencePenalty)
                    .responseLogprobs(responseLogprobs)
                    .returnThinking(returnThinking)
                    .safetySettings(safetySettings)
                    .seed(seed)
                    .stopSequences(stopSequences)
                    .temperature(temperature)
                    .thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(includeThoughts)
                            .thinkingBudget(thinkingBudget)
                            .build())
                    .timeout(connectTimeOut)
                    .topK(topK)
                    .topP(topP);
            if (isJson) {
                builder.responseFormat(ResponseFormat.JSON);
            }
            if(httpClientBuilder != null) {
                builder.httpClientBuilder(httpClientBuilder);
            }
            if (listeners != null && !listeners.isEmpty()) {
                builder.listeners(listeners);
            }
            instance = builder.build();
        }
        return (StreamingChatModel) instance;
    }

    public WildFlyGeminiChatModelConfig allowCodeExecution(Boolean allowCodeExecution) {
        this.allowCodeExecution = allowCodeExecution;
        return this;
    }

    public WildFlyGeminiChatModelConfig apiKey(String key) {
        this.key = key;
        return this;
    }

    public WildFlyGeminiChatModelConfig enableEnhancedCivicAnswers(Boolean enableEnhancedCivicAnswers) {
        this.enableEnhancedCivicAnswers = enableEnhancedCivicAnswers;
        return this;
    }

    public WildFlyGeminiChatModelConfig frequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
        return this;
    }

    public WildFlyGeminiChatModelConfig httpClientBuilder(HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return this;
    }

    public WildFlyGeminiChatModelConfig includeCodeExecutionOutput(Boolean includeCodeExecutionOutput) {
        this.includeCodeExecutionOutput = includeCodeExecutionOutput;
        return this;
    }

    public WildFlyGeminiChatModelConfig includeThoughts(Boolean includeThoughts) {
        this.includeThoughts = includeThoughts;
        return this;
    }

    public WildFlyGeminiChatModelConfig logprobs(Integer logprobs) {
        this.logprobs = logprobs;
        return this;
    }

    public WildFlyGeminiChatModelConfig logRequests(Boolean logRequests) {
        this.logRequests = logRequests;
        return this;
    }

    public WildFlyGeminiChatModelConfig logResponses(Boolean logResponses) {
        this.logResponses = logResponses;
        return this;
    }

    public WildFlyGeminiChatModelConfig maxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
        return this;
    }

    public WildFlyGeminiChatModelConfig modelName(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public WildFlyGeminiChatModelConfig presencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
        return this;
    }

    public WildFlyGeminiChatModelConfig responseLogprobs(Boolean responseLogprobs) {
        this.responseLogprobs = responseLogprobs;
        return this;
    }

    public WildFlyGeminiChatModelConfig returnThinking(Boolean returnThinking) {
        this.returnThinking = returnThinking;
        return this;
    }

    public WildFlyGeminiChatModelConfig safetySettings(Map<String, String> safetySettingsConfig) {
        this.safetySettings = new ArrayList<>();
        for (Entry<String, String> safetySetting : safetySettingsConfig.entrySet()) {
            try {
                GeminiHarmCategory category = GeminiHarmCategory.valueOf(safetySetting.getKey());
                GeminiHarmBlockThreshold threshold = GeminiHarmBlockThreshold.valueOf(safetySetting.getValue());
                this.safetySettings.add(new GeminiSafetySetting(category, threshold));
            } catch (IllegalArgumentException ex) {
                AILogger.ROOT_LOGGER.invalidGeminiSafetySetting(ex, safetySetting.getKey(), safetySetting.getValue());
            }
        }
        return this;
    }

    public WildFlyGeminiChatModelConfig seed(Integer seed) {
        this.seed = seed;
        return this;
    }

    public WildFlyGeminiChatModelConfig setJson(boolean isJson) {
        this.isJson = isJson;
        return this;
    }

    public WildFlyGeminiChatModelConfig setObservable(boolean observable) {
        this.observable = observable;
        return this;
    }

    public WildFlyGeminiChatModelConfig setStreaming(boolean streaming) {
        this.streaming = streaming;
        return this;
    }

    public WildFlyGeminiChatModelConfig stopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
        return this;
    }

    public WildFlyGeminiChatModelConfig temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public WildFlyGeminiChatModelConfig thinkingBudget(Integer thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
        return this;
    }

    public WildFlyGeminiChatModelConfig timeout(long timeOut) {
        if (timeOut <= 0L) {
            this.connectTimeOut = null;
            return this;
        }
        this.connectTimeOut = Duration.ofMillis(timeOut);
        return this;
    }

    public WildFlyGeminiChatModelConfig topK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public WildFlyGeminiChatModelConfig topP(Double topP) {
        this.topP = topP;
        return this;
    }

    @Override
    public boolean isStreaming() {
        return streaming;
    }

    @Override
    public boolean isObservable() {
        return observable;
    }
}
