package org.wildfly.ai.test.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.ai.test.util.DeploymentFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Ollama-based embedding model in WildFly.
 *
 * <p>This test case validates the {@code ai-embedding-ollama} Galleon layer by testing:</p>
 * <ul>
 *   <li>CDI injection of Ollama-based {@link EmbeddingModel} beans</li>
 *   <li>Single text embedding generation using Ollama</li>
 *   <li>Batch embedding processing</li>
 * </ul>
 *
 * <p>Unlike All-MiniLM-L6-v2 which runs locally, this embedding model uses the
 * Ollama service to generate embeddings via the llama3.2:1b model. The embedding
 * dimensions depend on the specific Ollama model being used.</p>
 *
 * @see DeploymentFactory
 * @see EmbeddingModel
 */
@ExtendWith(ArquillianExtension.class)
public class OllamaEmbeddingModelTestCase {

    /**
     * Creates the test deployment archive.
     *
     * @return a WAR archive configured for Ollama embedding testing
     */
    @Deployment
    public static WebArchive createDeployment() {
        return DeploymentFactory.createBaseDeployment("ollama-embedding-test.war");
    }

    @Inject
    @Named("ollama-embeddings")
    private EmbeddingModel embeddingModel;

    /**
     * Verifies that the Ollama EmbeddingModel bean is properly injected via CDI.
     *
     * <p>This test ensures the WildFly AI subsystem correctly registers
     * and makes available the Ollama embedding model.</p>
     */
    @Test
    public void testEmbeddingModelInjection() {
        assertThat(embeddingModel)
                .as("EmbeddingModel should be injected via CDI")
                .isNotNull();
    }

    /**
     * Tests generation of embeddings for a single text input using Ollama.
     *
     * <p>Validates that the Ollama service produces a valid embedding vector
     * with positive dimensions for a given text input.</p>
     */
    @Test
    public void testSingleTextEmbedding() {
        String text = "This is a test sentence for Ollama embedding.";
        Embedding embedding = embeddingModel.embed(text).content();

        assertThat(embedding)
                .as("Embedding should be generated")
                .isNotNull();

        assertThat(embedding.vector())
                .as("Embedding vector should not be empty")
                .isNotEmpty();

        assertThat(embedding.dimension())
                .as("Embedding dimension should be positive")
                .isPositive();
    }

    /**
     * Tests batch embedding generation for multiple text inputs using Ollama.
     *
     * <p>Validates that the Ollama service can efficiently process multiple text
     * segments in a batch operation, with each segment producing an embedding vector.</p>
     */
    @Test
    public void testBatchEmbedding() {
        List<TextSegment> textSegments = List.of(
                TextSegment.from("First Ollama test sentence."),
                TextSegment.from("Second Ollama test sentence.")
        );

        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();

        assertThat(embeddings)
                .as("Should generate embeddings for all texts")
                .hasSize(2);

        for (Embedding embedding : embeddings) {
            assertThat(embedding.dimension())
                    .as("Each embedding should have positive dimensions")
                    .isPositive();
        }
    }
}
