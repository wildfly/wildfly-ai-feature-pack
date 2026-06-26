package org.wildfly.ai.test.chatmodel;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.ai.test.util.DeploymentFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Ollama chat model functionality in WildFly.
 *
 * <p>This test case validates the {@code ai-chat-ollama} Galleon layer by testing:</p>
 * <ul>
 *   <li>CDI injection of {@link ChatModel} beans</li>
 *   <li>Basic chat interaction with the Ollama model</li>
 *   <li>Question answering capabilities</li>
 * </ul>
 *
 * @see DeploymentFactory
 */
@ExtendWith(ArquillianExtension.class)
public class OllamaChatModelTestCase {

    /**
     * Creates the test deployment archive.
     *
     * @return a WAR archive configured for Ollama chat model testing
     */
    @Deployment
    public static WebArchive createDeployment() {
        return DeploymentFactory.createBaseDeployment("ollama-chat-test.war");
    }

    @Inject
    @Named("ollama")
    private ChatModel chatModel;

    /**
     * Verifies that the ChatModel bean is properly injected via CDI.
     *
     * <p>This test ensures the WildFly AI subsystem correctly registers
     * and makes available the Ollama chat model for dependency injection.</p>
     */
    @Test
    public void testChatModelInjection() {
        assertThat(chatModel)
                .as("ChatModel should be injected via CDI")
                .isNotNull();
    }

    /**
     * Tests basic chat interaction with the Ollama model.
     *
     * <p>Sends a simple prompt and verifies that the model generates a
     * meaningful response containing the expected greeting.</p>
     */
    @Test
    public void testBasicChatInteraction() {
        String response = chatModel.chat("Say 'Hello, WildFly AI!' and nothing else.");

        assertThat(response)
                .as("Chat model should generate a response")
                .isNotNull()
                .isNotEmpty()
                .containsIgnoringCase("Hello");
    }

    /**
     * Tests the model's ability to answer simple mathematical questions.
     *
     * <p>Validates that the Ollama model can process and respond to
     * basic arithmetic problems correctly.</p>
     */
    @Test
    public void testMathQuestion() {
        String response = chatModel.chat("What is 2+2? Answer with just the number.");

        assertThat(response)
                .as("Chat model should correctly answer simple math")
                .isNotNull()
                .contains("4");
    }
}
