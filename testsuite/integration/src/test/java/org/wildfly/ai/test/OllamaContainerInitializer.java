package org.wildfly.ai.test;

import org.junit.platform.launcher.TestExecutionListener;
import org.wildfly.ai.test.container.OllamaContainerManager;

/**
 * Test execution listener that ensures Ollama container is initialized before any tests run.
 *
 * <p>This listener is automatically discovered by JUnit Platform and ensures the
 * {@link OllamaContainerManager} initializes the Ollama container before test
 * execution begins.</p>
 *
 * <p>To register this listener, create a file at:</p>
 * <pre>
 * src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener
 * </pre>
 * <p>containing the fully qualified class name of this listener.</p>
 */
public class OllamaContainerInitializer implements TestExecutionListener {

    static {
        // Initialization already happened in OllamaContainerManager static block.
        // Just report the status here (same pattern as LgtmContainerInitializer).
        if (OllamaContainerManager.isAvailable()) {
            System.out.println("=================================================");
            System.out.println("Ollama initialized at: " + OllamaContainerManager.getEndpoint());
            System.out.println("Ollama server version: " + OllamaContainerManager.getServerVersion());
            System.out.println("Chat model: " + OllamaContainerManager.getModelName());
            System.out.println("Embedding model: " + OllamaContainerManager.getEmbeddingModelName());
            System.out.println("Embeddings: " + OllamaContainerManager.getEmbeddingDiagnostic());
            System.out.println("=================================================");
        } else {
            System.out.println("=================================================");
            System.out.println("Ollama not available - chat/embedding tests disabled");
            System.out.println("To enable: start Ollama locally on port 11434");
            System.out.println("  or ensure Docker is available");
            System.out.println("=================================================");
        }
    }
}
