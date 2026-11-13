package org.wildfly.ai.test.observable;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.ai.test.util.DeploymentFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the non-observable server (no OpenTelemetry subsystem).
 *
 * <p>Validates that when {@code observable=false}, the telemetry listeners
 * ({@code GenAISpanChatModelListener} and {@code GenAIMetricsChatModelListener}) are not
 * baked into the chat model at construction time. Verified via reflection on the
 * model's internal listener list.</p>
 *
 * <p>Tests that require the OpenTelemetry subsystem to be active (e.g. checking
 * that telemetry listeners are present in CDI) belong in
 * {@link ObservableChatModelTestCase}, which targets the {@code wildfly-observable}
 * container.</p>
 */
@ExtendWith(ArquillianExtension.class)
public class NonObservableChatModelTestCase extends AbstractObservableChatModelTestCase {

    @Deployment
    public static WebArchive createDeployment() {
        return DeploymentFactory.createBaseDeployment("non-observable-chat-test.war")
                .addClasses(TestChatModelListener.class, AbstractObservableChatModelTestCase.class);
    }

    // ---- shared tests (must be declared here for Arquillian in-container routing) ----

    @Test
    public void testChatModelInjected() {
        assertThat(chatModel).as("Chat model should be injected").isNotNull();
    }

    @Test
    public void testCustomListenerInjected() {
        assertThat(testListener).as("TestChatModelListener should be injected").isNotNull();
    }

    @Test
    public void testListenersDiscoverable() {
        List<ChatModelListener> listeners = StreamSupport.stream(allListeners.spliterator(), false)
                .collect(Collectors.toList());
        assertThat(listeners).as("At least one ChatModelListener should be available").isNotEmpty();
        assertThat(listeners).as("TestChatModelListener should be discovered")
                .anyMatch(l -> l instanceof TestChatModelListener);
    }

    @Test
    public void testChatModelFunctionality() {
        String response = chatModel.chat("Say 'Test' and nothing else.");
        assertThat(response).as("Chat model should generate responses").isNotNull().isNotEmpty();
    }

    @Test
    public void testCustomListenerState() {
        assertThat(testListener).as("Test listener should be injectable").isNotNull();
    }

    // ---- test specific to the non-observable (no OTel) server ----

    /**
     * Uses reflection to read the listeners list that was baked into the injected ChatModel
     * at construction time by WildFlyLLMConfig.
     *
     * <p>Because the chatModel is an {@code @ApplicationScoped} CDI proxy (Weld), we first
     * unwrap it to the real contextual instance via {@code WeldClientProxy}, then walk up
     * the class hierarchy to find the {@code listeners} field.</p>
     *
     * <p>With {@code observable=false} the production filter should have already stripped
     * {@code GenAISpanChatModelListener} and {@code GenAIMetricsChatModelListener} before passing
     * the list to the builder, so neither should appear here.</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testChatModelListenersViaReflection() throws Exception {
        Object actualModel = unwrapCdiProxy(chatModel);

        List<ChatModelListener> modelListeners = findListenersField(actualModel);

        System.out.println("=== ChatModel internal listeners (via reflection) ===");
        System.out.println("Model class: " + actualModel.getClass().getName());
        System.out.println("Listener count: " + modelListeners.size());
        modelListeners.forEach(l -> System.out.println("  " + l.getClass().getName()));
        System.out.println("====================================================");

        assertThat(modelListeners)
                .as("Telemetry listeners must not be baked into the model when observable=false")
                .extracting(l -> l.getClass().getName())
                .doesNotContain(SPAN_LISTENER, METRICS_LISTENER);
    }

    /**
     * Unwraps a Weld CDI client proxy to retrieve the actual contextual bean instance.
     * Falls back to the proxy itself if the Weld API is unavailable.
     */
    private static Object unwrapCdiProxy(Object proxy) {
        try {
            Class<?> weldProxyClass = Class.forName("org.jboss.weld.proxy.WeldClientProxy");
            if (weldProxyClass.isInstance(proxy)) {
                Object metadata = weldProxyClass.getMethod("getMetadata").invoke(proxy);
                return metadata.getClass().getMethod("getContextualInstance").invoke(metadata);
            }
        } catch (Exception ignored) {
            // Not a Weld proxy or unavailable — proceed with the proxy itself
        }
        return proxy;
    }

    /**
     * Walks the class hierarchy of {@code model} to find a field named {@code listeners}
     * and returns its value.
     */
    @SuppressWarnings("unchecked")
    private static List<ChatModelListener> findListenersField(Object model) throws Exception {
        Class<?> clazz = model.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField("listeners");
                field.setAccessible(true);
                return (List<ChatModelListener>) field.get(model);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("No 'listeners' field found in class hierarchy of " + model.getClass().getName());
    }
}
