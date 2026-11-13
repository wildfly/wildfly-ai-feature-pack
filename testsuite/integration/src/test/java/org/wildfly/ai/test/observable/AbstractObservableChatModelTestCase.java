package org.wildfly.ai.test.observable;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared base for observable/non-observable chat model integration tests.
 *
 * <p>Provides the common CDI injections, listener constants, and lifecycle setup.
 * {@code @Test} methods are intentionally NOT declared here — Arquillian JUnit5
 * cannot route in-container test execution for methods whose declaring class differs
 * from the concrete test class. Each subclass re-declares its own {@code @Test} methods.</p>
 */
abstract class AbstractObservableChatModelTestCase {

    static final String SPAN_LISTENER    = "dev.langchain4j.cdi.telemetry.GenAISpanChatModelListener";
    static final String METRICS_LISTENER = "dev.langchain4j.cdi.telemetry.GenAIMetricsChatModelListener";

    @Inject
    @Named("ollama")
    ChatModel chatModel;

    @Inject
    Instance<ChatModelListener> allListeners;

    @Inject
    TestChatModelListener testListener;

    @BeforeEach
    public void resetListener() {
        testListener.reset();
    }

    /** Returns true for listeners that WildFlyLLMConfig removes when observable=false. */
    static boolean isObservabilityListener(ChatModelListener l) {
        String name = l.getClass().getName();
        return name.equals(SPAN_LISTENER) || name.equals(METRICS_LISTENER);
    }
}
