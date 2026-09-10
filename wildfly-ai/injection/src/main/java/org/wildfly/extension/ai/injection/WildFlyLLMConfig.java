/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.injection;

import static org.wildfly.extension.ai.injection.AILogger.ROOT_LOGGER;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.cdi.core.config.spi.LLMConfig;
import dev.langchain4j.cdi.core.config.spi.ProducerFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Instance.Handle;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.wildfly.extension.ai.injection.chat.WildFlyChatModelConfig;
import org.wildfly.extension.ai.injection.memory.WildFlyChatMemoryProviderConfig;
import org.wildfly.extension.ai.injection.retriever.WildFlyContentRetrieverConfig;

/**
 * WildFly implementation of LangChain4j's LLMConfig SPI.
 *
 * <p>This class bridges WildFly's AI subsystem configuration with LangChain4j's CDI
 * integration by implementing the {@link LLMConfig} service provider interface. It
 * manages bean registration, property configuration, and producer function creation
 * for all AI service types.</p>
 *
 * <p>The configuration handles producer functions for:</p>
 * <ul>
 *   <li>Chat models with optional observability ({@link ChatModelListener})</li>
 *   <li>Streaming chat models</li>
 *   <li>Content retrievers with dependency injection support</li>
 *   <li>Chat memory providers</li>
 *   <li>Other AI services (embedding models, stores, etc.)</li>
 * </ul>
 *
 * <p>All beans are registered with {@link ApplicationScoped} scope and are made
 * available for injection using {@code @Named} qualifiers.</p>
 *
 * @see LLMConfig
 * @see WildFlyBeanRegistry
 */
public class WildFlyLLMConfig extends LLMConfig {

    private static final Set<String> beanNames = new HashSet<>();
    private static final Map<String, String> values = new HashMap<>();

    /**
     * Registers an AI service bean for CDI production.
     *
     * <p>This method configures the bean metadata (class, scope, producer) and creates
     * a producer function that will be called by the CDI container when the bean is
     * requested for injection.</p>
     *
     * @param name unique bean identifier (used with {@code @Named} qualifier)
     * @param value configuration or instance for the bean
     * @param type expected bean type (e.g., ChatModel.class, EmbeddingModel.class)
     */
    public void registerBean(String name, Object value, Class<?> type) {
        beanNames.add(name);
        values.put(getBeanPropertyName(name, CLASS), type.getName());
        values.put(getBeanPropertyName(name, SCOPE), ApplicationScoped.class.getName());
        values.put(getBeanPropertyName(name, PRODUCER), getBeanPropertyName(name, PRODUCER));
        registerProducer(getBeanPropertyName(name, PRODUCER), createProducerFunction(value, type));
    }

    /**
     * Checks if a ChatModelListener is an observability listener that should be filtered
     * when observable is set to false.
     *
     * @param listener the ChatModelListener to check
     * @return true if the listener is SpanChatModelListener or MetricsChatModelListener
     */
    private boolean isObservabilityListener(ChatModelListener listener) {
        // Class names are matched as strings intentionally: importing the real classes would
        // introduce a compile-time dependency on langchain4j-cdi, which is an optional runtime
        // module not guaranteed to be present in all WildFly configurations.
        String className = listener.getClass().getName();
        return className.equals("dev.langchain4j.cdi.telemetry.SpanChatModelListener") ||
               className.equals("dev.langchain4j.cdi.telemetry.MetricsChatModelListener");
    }

    /**
     * Creates a producer function for an AI service bean.
     *
     * <p>The producer function is type-specific and handles:</p>
     * <ul>
     *   <li><b>Chat models</b> - Creates ChatModel or StreamingChatModel with optional listeners</li>
     *   <li><b>Content retrievers</b> - Creates ContentRetriever with CDI dependencies</li>
     *   <li><b>Chat memory</b> - Creates ChatMemoryProvider with CDI dependencies</li>
     *   <li><b>Other types</b> - Returns the value directly</li>
     * </ul>
     *
     * @param value configuration object for the bean
     * @param expectedType expected bean type for validation
     * @return producer function that creates the bean instance
     */
    private ProducerFunction<Object> createProducerFunction(Object value, Class<?> expectedType) {
            if (ChatModel.class.isAssignableFrom(expectedType) || StreamingChatModel.class.isAssignableFrom(expectedType)) {
                return (Instance<Object> lookup, String beanName, LLMConfig llmConfig) -> {
                    WildFlyChatModelConfig config = (WildFlyChatModelConfig) value;
                    List<ChatModelListener> listeners;
                    if(config.isObservable()) {
                        listeners = lookup.select(ChatModelListener.class).handlesStream().map(Handle<ChatModelListener>::get).collect(Collectors.toList());
                    } else {
                        // When observable is false, filter out SpanChatModelListener and MetricsChatModelListener
                        listeners = lookup.select(ChatModelListener.class).handlesStream()
                                .map(Handle<ChatModelListener>::get)
                                .filter(listener -> !isObservabilityListener(listener))
                                .collect(Collectors.toList());
                    }
                    ROOT_LOGGER.beanProduced(beanName, expectedType);
                    if (ChatModel.class.isAssignableFrom(expectedType) && !config.isStreaming()) {
                        return config.createLanguageModel(listeners);
                    }
                    if (StreamingChatModel.class.isAssignableFrom(expectedType) && config.isStreaming()) {
                        return config.createStreamingLanguageModel(listeners);
                    }
                    throw ROOT_LOGGER.incorrectLLMConfiguration(beanName, expectedType.getName(), config.isStreaming());
                };
            } else if (ContentRetriever.class.isAssignableFrom(expectedType)) {
                return (Instance<Object> lookup, String beanName, LLMConfig llmConfig) -> {
                    ROOT_LOGGER.beanProduced(beanName, expectedType);
                    WildFlyContentRetrieverConfig config = (WildFlyContentRetrieverConfig) value;
                    return config.createContentRetriever(lookup);
                };
            } else if (ChatMemoryProvider.class.isAssignableFrom(expectedType)) {
                return (Instance<Object> lookup, String beanName, LLMConfig llmConfig) -> {
                    ROOT_LOGGER.beanProduced(beanName, expectedType);
                    WildFlyChatMemoryProviderConfig config = (WildFlyChatMemoryProviderConfig) value;
                    return config.createChatMemory(lookup);
                };
            } else {
                return (Instance<Object> lookup, String beanName, LLMConfig llmConfig) -> {
                    ROOT_LOGGER.beanProduced(beanName, expectedType);
                    return value;
                };
            }
    }

    /**
     * Initializes the configuration.
     *
     * <p>No initialization is required as beans are registered dynamically
     * by the WildFly subsystem during deployment processing.</p>
     */
    @Override
    public void init() {
    }

    /**
     * Returns the set of all registered bean names.
     *
     * @return unmodifiable set of bean identifiers
     */
    @Override
    public Set<String> getBeanNames() {
        return Collections.unmodifiableSet(beanNames);
    }

    /**
     * Returns property names for a specific bean.
     *
     * <p>WildFly manages bean properties internally, so this returns an empty set
     * as properties are not exposed through the LLMConfig interface.</p>
     *
     * @param beanName the bean identifier
     * @return empty set (properties managed internally)
     */
    @Override
    public Set<String> getPropertyNamesForBean(String beanName) {
        return Collections.emptySet();
    }

    /**
     * Constructs a fully-qualified property name for a bean attribute.
     *
     * @param beanName the bean identifier
     * @param propertyName the property name (e.g., "class", "scope", "producer")
     * @return qualified property name in format "langchain4j.{beanName}.{propertyName}"
     */
    private String getBeanPropertyName(String beanName, String propertyName) {
        return PREFIX + "." + beanName + "." + propertyName;
    }

    /**
     * Returns all registered property keys.
     *
     * @return set of property keys for all registered beans
     */
    @Override
    public Set<String> getPropertyKeys() {
        return values.keySet();
    }

    /**
     * Retrieves a property value by key.
     *
     * @param name the property key
     * @return property value, or null if not found
     */
    @Override
    public String getValue(String name) {
        return values.get(name);
    }
}
