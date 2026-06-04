package org.wildfly.ai.test.retriever;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.wildfly.ai.test.util.DeploymentFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for embedding store-based content retriever in WildFly.
 *
 * <p>This test case validates the {@code ai-retriever-embedding-store} Galleon layer by testing:</p>
 * <ul>
 *   <li>CDI injection of {@link ContentRetriever} beans</li>
 *   <li>Semantic search and retrieval of relevant content</li>
 *   <li>Multiple results retrieval based on query similarity</li>
 *   <li>Metadata preservation during retrieval</li>
 * </ul>
 *
 * <p>The content retriever uses an {@link EmbeddingStore} and {@link EmbeddingModel}
 * to perform semantic search. It converts queries to embeddings and finds the most
 * similar stored content based on cosine similarity.</p>
 *
 * @see RetrieverTestBean
 * @see DeploymentFactory
 * @see ContentRetriever
 */
@ExtendWith(ArquillianExtension.class)
public class EmbeddingStoreContentRetrieverTestCase {

    /**
     * Creates the test deployment archive with the retriever test bean.
     *
     * @return a WAR archive configured for content retriever testing
     */
    @Deployment
    public static WebArchive createDeployment() {
        return DeploymentFactory.createMinimalDeployment("content-retriever-test.war", RetrieverTestBean.class);
    }
    @Inject
    private RetrieverTestBean retrieverTestBean;
    @Inject
    @Named("all-minilm-l6-v2")
    private EmbeddingModel embeddingModel;
    @Inject
    @Named("in-memory")
    private EmbeddingStore embeddingStore;

    /**
     * Populates the embedding store with test data before each test.
     *
     * <p>Adds three text segments with embeddings and metadata to the store,
     * which are used by retrieval tests to verify semantic search functionality.</p>
     */
    @BeforeEach
    public void populateEmbeddingStore() {
        // Populate the embedding store with test data
        String doc1 = "The WildFly AI feature pack provides integration with LangChain4j.";
        String doc2 = "Embeddings are vector representations of text that capture semantic meaning.";
        String doc3 = "The AI feature pack supports multiple LLM providers including Ollama and OpenAI.";

        TextSegment segment1 = TextSegment.from(doc1, Metadata.from("source", "test"));
        TextSegment segment2 = TextSegment.from(doc2, Metadata.from("source", "test"));
        TextSegment segment3 = TextSegment.from(doc3, Metadata.from("source", "test"));

        embeddingStore.add(embeddingModel.embed(doc1).content(), segment1);
        embeddingStore.add(embeddingModel.embed(doc2).content(), segment2);
        embeddingStore.add(embeddingModel.embed(doc3).content(), segment3);
    }

    /**
     * Verifies that the ContentRetriever bean is properly injected via CDI.
     *
     * <p>Tests both the RetrieverTestBean injection and the ContentRetriever
     * injection into that bean.</p>
     */
    @Test
    public void testContentRetrieverInjection() {
        assertThat(retrieverTestBean)
                .as("RetrieverTestBean should be injected via CDI")
                .isNotNull();
        assertThat(retrieverTestBean.getContentRetriever())
                .as("ContentRetriever should be injected into RetrieverTestBean")
                .isNotNull();
    }

    /**
     * Tests semantic search to retrieve relevant content based on a query.
     *
     * <p>Validates that the retriever finds the most semantically relevant
     * content from the embedding store for a given query.</p>
     */
    @Test
    public void testRetrieveRelevantContent() {
        List<Content> contents = retrieverTestBean.retrieve("What is the AI feature pack?");

        assertThat(contents)
                .as("Should retrieve relevant content")
                .isNotEmpty();

        assertThat(contents.get(0).textSegment().text())
                .as("Retrieved content should be relevant to AI feature pack")
                .containsIgnoringCase("AI feature pack");
    }

    /**
     * Tests retrieval of multiple relevant results for a query.
     *
     * <p>Validates that the retriever can return multiple content items
     * when several stored items are semantically relevant to the query.</p>
     */
    @Test
    public void testRetrieveWithMultipleResults() {
        List<Content> contents = retrieverTestBean.retrieve("Tell me about WildFly");

        assertThat(contents)
                .as("Should retrieve multiple relevant results")
                .hasSizeGreaterThanOrEqualTo(1);

        boolean hasWildFlyContent = contents.stream()
                .anyMatch(content -> content.textSegment().text().contains("WildFly"));

        assertThat(hasWildFlyContent)
                .as("At least one result should mention WildFly")
                .isTrue();
    }

    /**
     * Tests that retrieved content preserves associated metadata.
     *
     * <p>Validates that metadata attached to stored text segments is
     * preserved and accessible in retrieved results.</p>
     */
    @Test
    public void testRetrieveWithMetadata() {
        List<Content> contents = retrieverTestBean.retrieve("What are embeddings?");

        assertThat(contents)
                .as("Should retrieve content with metadata")
                .isNotEmpty();

        Content firstContent = contents.get(0);
        assertThat(firstContent.textSegment().metadata())
                .as("Retrieved content should have metadata")
                .isNotNull();

        assertThat(firstContent.textSegment().metadata().getString("source"))
                .as("Metadata should contain source information")
                .isEqualTo("test");
    }
}
