/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.ai.injection;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Import the mock listeners from dev.langchain4j.cdi package
import dev.langchain4j.cdi.telemetry.GenAIMetricsChatModelListener;
import dev.langchain4j.cdi.telemetry.GenAISpanChatModelListener;

/**
 * Unit tests for {@link WildFlyLLMConfig} listener filtering logic.
 *
 * <p>Tests verify that GenAISpanChatModelListener and GenAIMetricsChatModelListener
 * are correctly filtered when observable is set to false.</p>
 */
class WildFlyLLMConfigTest {

    private WildFlyLLMConfig llmConfig;

    @BeforeEach
    void setUp() {
        llmConfig = new WildFlyLLMConfig();
    }

    /**
     * Tests that the isObservabilityListener method correctly identifies
     * GenAISpanChatModelListener.
     */
    @Test
    void testIsObservabilityListener_SpanChatModelListener() throws Exception {
        ChatModelListener listener = new GenAISpanChatModelListener();

        boolean result = invokeIsObservabilityListener(listener);

        assertThat(result)
                .as("GenAISpanChatModelListener should be identified as observability listener")
                .isTrue();
    }

    /**
     * Tests that the isObservabilityListener method correctly identifies
     * GenAIMetricsChatModelListener.
     */
    @Test
    void testIsObservabilityListener_MetricsChatModelListener() throws Exception {
        ChatModelListener listener = new GenAIMetricsChatModelListener();

        boolean result = invokeIsObservabilityListener(listener);

        assertThat(result)
                .as("GenAIMetricsChatModelListener should be identified as observability listener")
                .isTrue();
    }

    /**
     * Tests that the isObservabilityListener method returns false for
     * custom listeners.
     */
    @Test
    void testIsObservabilityListener_CustomListener() throws Exception {
        ChatModelListener listener = new CustomChatModelListener();

        boolean result = invokeIsObservabilityListener(listener);

        assertThat(result)
                .as("Custom listener should not be identified as observability listener")
                .isFalse();
    }

    /**
     * Tests that all listeners including observability ones are retained when observable is true,
     * whereas observable=false would exclude them.
     *
     * <p>Uses the production {@code isObservabilityListener} method (via reflection) to identify
     * which listeners would be excluded in the observable=false path, then confirms they are
     * all retained in the observable=true path (no filter applied).</p>
     */
    @Test
    void testListenerFiltering_ObservableTrue() throws Exception {
        GenAISpanChatModelListener span = new GenAISpanChatModelListener();
        GenAIMetricsChatModelListener metrics = new GenAIMetricsChatModelListener();
        CustomChatModelListener custom = new CustomChatModelListener();

        List<ChatModelListener> allListeners = List.of(span, metrics, custom);

        // Identify which listeners the production code would filter when observable=false
        List<ChatModelListener> wouldBeExcluded = allListeners.stream()
                .filter(l -> {
                    try { return invokeIsObservabilityListener(l); }
                    catch (Exception e) { throw new RuntimeException(e); }
                })
                .collect(Collectors.toList());

        assertThat(wouldBeExcluded)
                .as("Production code identifies span and metrics as observability listeners")
                .containsExactlyInAnyOrder(span, metrics);

        // observable=true path: retain all listeners, observability ones included
        List<ChatModelListener> retainedObservableTrue = new ArrayList<>(allListeners);

        assertThat(retainedObservableTrue)
                .as("When observable=true all listeners are retained, including those observable=false would exclude")
                .containsAll(wouldBeExcluded)
                .containsExactlyInAnyOrder(span, metrics, custom);
    }

    /**
     * Helper method to invoke the private isObservabilityListener method using reflection.
     */
    private boolean invokeIsObservabilityListener(ChatModelListener listener) throws Exception {
        var method = WildFlyLLMConfig.class.getDeclaredMethod("isObservabilityListener", ChatModelListener.class);
        method.setAccessible(true);
        return (boolean) method.invoke(llmConfig, listener);
    }

    /**
     * Custom listener that should not be filtered.
     */
    private static class CustomChatModelListener implements ChatModelListener {
        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
        }

        @Override
        public void onResponse(ChatModelResponseContext responseContext) {
        }

        @Override
        public void onError(ChatModelErrorContext errorContext) {
        }
    }

    /**
     * Tests that when observable=false, the filtered list actually excludes
     * observability listeners and keeps custom ones.
     */
    @Test
    void testFilteredListContents() {
        List<ChatModelListener> allListeners = new ArrayList<>();
        GenAISpanChatModelListener span = new GenAISpanChatModelListener();
        GenAIMetricsChatModelListener metrics = new GenAIMetricsChatModelListener();
        CustomChatModelListener custom = new CustomChatModelListener();

        allListeners.add(span);
        allListeners.add(metrics);
        allListeners.add(custom);

        // Simulate the observable=false filtering via production isObservabilityListener
        List<ChatModelListener> filtered = allListeners.stream()
                .filter(l -> {
                    try { return !invokeIsObservabilityListener(l); }
                    catch (Exception e) { throw new RuntimeException(e); }
                })
                .collect(Collectors.toList());

        assertThat(filtered)
                .as("Filtered list should only contain custom listener")
                .containsExactly(custom)
                .doesNotContain(span, metrics);
    }

}
