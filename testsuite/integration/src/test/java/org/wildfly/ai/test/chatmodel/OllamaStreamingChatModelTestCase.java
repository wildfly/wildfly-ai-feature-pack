package org.wildfly.ai.test.chatmodel;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.ai.test.util.DeploymentFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Ollama streaming chat model functionality in WildFly.
 *
 * <p>This test case validates the {@code ai-chat-ollama-streaming} Galleon layer by testing:</p>
 * <ul>
 *   <li>CDI injection of {@link StreamingChatModel} beans</li>
 *   <li>Streaming response generation and token-by-token processing</li>
 *   <li>Response completion callbacks</li>
 *   <li>Multiple sequential streaming requests</li>
 * </ul>
 *
 * @see DeploymentFactory
 * @see StreamingChatModel
 */
@ExtendWith(ArquillianExtension.class)
public class OllamaStreamingChatModelTestCase {

    @Deployment
    public static WebArchive createDeployment() {
        return DeploymentFactory.createBaseDeployment("ollama-streaming-chat-test.war");
    }

    @Inject
    @Named("streaming-ollama")
    private StreamingChatModel streamingChatModel;

    /**
     * Sends a prompt to the streaming model and returns a future that completes
     * with the full accumulated response text.
     */
    private CompletableFuture<String> chatAsync(String prompt) {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder accumulated = new StringBuilder();
        streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                accumulated.append(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse cr) {
                future.complete(accumulated.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    /**
     * Verifies that the StreamingChatModel bean is properly injected via CDI.
     */
    @Test
    public void testStreamingChatModelInjection() {
        assertThat(streamingChatModel)
                .as("StreamingChatModel should be injected via CDI")
                .isNotNull();
    }

    /**
     * Tests basic streaming response generation with token accumulation.
     *
     * <p>Validates that the streaming model sends partial responses (tokens)
     * progressively and completes with the full response.</p>
     *
     * @throws Exception if the streaming operation fails or times out
     */
    @Test
    public void testBasicStreamingResponse() throws Exception {
        String response = chatAsync("Say 'Hello, WildFly AI!' and nothing else.")
                .get(30, TimeUnit.SECONDS);

        assertThat(response)
                .as("Streaming model should generate a response")
                .isNotNull()
                .isNotEmpty()
                .containsIgnoringCase("Hello");
    }

    /**
     * Tests token-by-token collection during streaming response generation.
     *
     * <p>Verifies that each partial response (token) is captured individually
     * during the streaming process.</p>
     *
     * @throws Exception if the streaming operation fails or times out
     */
    @Test
    public void testStreamingTokenCollection() throws Exception {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        List<String> tokens = new ArrayList<>();

        streamingChatModel.chat("Count from 1 to 3, just the numbers separated by spaces.",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        tokens.add(token);
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse cr) {
                        future.complete(tokens);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });

        List<String> receivedTokens = future.get(30, TimeUnit.SECONDS);

        assertThat(receivedTokens)
                .as("Should receive multiple tokens during streaming")
                .isNotEmpty();

        assertThat(String.join("", receivedTokens))
                .as("Combined tokens should form the complete response")
                .isNotNull()
                .isNotEmpty();
    }

    /**
     * Tests the response completion callback with ChatResponse metadata.
     *
     * <p>Validates that the {@code onCompleteResponse} callback is invoked with
     * a complete {@link ChatResponse} object containing the AI message and metadata.</p>
     *
     * @throws Exception if the streaming operation fails or times out
     */
    @Test
    public void testStreamingResponseCompletion() throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        streamingChatModel.chat("What is 2+2? Answer with just the number.",
                new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse cr) {
                        future.complete(cr);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });

        ChatResponse response = future.get(30, TimeUnit.SECONDS);

        assertThat(response)
                .as("Response object should be provided on completion")
                .isNotNull();

        assertThat(response.aiMessage())
                .as("Response should contain an AI message")
                .isNotNull();

        assertThat(response.aiMessage().text())
                .as("Response text should contain the answer")
                .isNotNull()
                .contains("4");
    }

    /**
     * Tests that the streaming model can handle multiple sequential requests.
     *
     * @throws Exception if any streaming operation fails or times out
     */
    @Test
    public void testStreamingWithMultipleRequests() throws Exception {
        String response1 = chatAsync("What is 1+1? Answer with just the number.")
                .get(30, TimeUnit.SECONDS);
        assertThat(response1)
                .as("First streaming response should complete")
                .contains("2");

        String response2 = chatAsync("What is 3+3? Answer with just the number.")
                .get(30, TimeUnit.SECONDS);
        assertThat(response2)
                .as("Second streaming response should complete")
                .contains("6");
    }
}
