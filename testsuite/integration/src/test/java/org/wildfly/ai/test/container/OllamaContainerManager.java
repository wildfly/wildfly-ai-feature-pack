package org.wildfly.ai.test.container;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton manager for the Ollama container lifecycle.
 *
 * <p>This manager ensures a single Ollama container is shared across all tests
 * to improve performance and reduce resource usage. It intelligently detects if
 * an Ollama instance is already running on the default port (11434) and reuses it,
 * or starts a new Testcontainers-managed instance if needed.</p>
 *
 * <p>The manager uses {@code ollama/ollama:latest} image and automatically pulls
 * the {@code llama3.2:1b} chat model and the {@code nomic-embed-text} embedding model
 * on first initialization.</p>
 *
 * <p><strong>Lifecycle Management:</strong></p>
 * <ul>
 *   <li>Initialization happens in static block before any tests run</li>
 *   <li>JVM shutdown hook registered to stop container when build finishes</li>
 *   <li>Only stops Testcontainers-managed instances (local instances remain untouched)</li>
 * </ul>
 *
 * <p>System properties set by this manager:</p>
 * <ul>
 *   <li>{@code ollama.base.url} - The endpoint URL for Ollama API</li>
 *   <li>{@code ollama.model.name} - The name of the pulled model (llama3.2:1b)</li>
 * </ul>
 *
 * @see org.wildfly.ai.test.OllamaContainerInitializer
 */
public class OllamaContainerManager {

    private static final String OLLAMA_IMAGE = "mirror.gcr.io/ollama/ollama:0.24.0";
    private static final String MODEL_NAME = "llama3.2:1b";
    private static final String EMBEDDING_MODEL_NAME = "nomic-embed-text";

    private static OllamaContainer ollama;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile boolean embeddingSupported = false;
    private static volatile String embeddingDiagnostic = null;
    private static volatile String serverVersion = null;

    /**
     * Static initializer that ensures Ollama is ready before any tests run.
     *
     * <p>Performs two operations:</p>
     * <ol>
     *   <li>Initializes the Ollama container or detects existing instance</li>
     *   <li>Registers JVM shutdown hook for automatic cleanup</li>
     * </ol>
     *
     * @throws RuntimeException if initialization fails
     */
    static {
        try {
            initializeContainer();
            registerShutdownHook();
            available = true;
        } catch (Throwable e) {
            System.err.println("Ollama initialization skipped: " + e.getMessage());
            System.err.println("Tests requiring Ollama will be disabled");
            available = false;
        }
    }

    /**
     * Registers a shutdown hook to stop the container when the JVM exits.
     * Only stops containers that were started by Testcontainers, not existing local instances.
     */
    private static void registerShutdownHook() {
        ContainerLifecycleUtil.registerShutdownHook(ollama, "Ollama");
    }

    /**
     * Initializes the Ollama container or detects an existing instance.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Checks if Ollama is already running on http://localhost:11434</li>
     *   <li>If found, reuses the existing instance to avoid port conflicts</li>
     *   <li>If not found, starts a new Testcontainers-managed Ollama container</li>
     *   <li>Pulls the llama3.2:1b model (one-time operation)</li>
     *   <li>Sets system properties for test access</li>
     * </ol>
     *
     * <p>This method is thread-safe and idempotent - subsequent calls after
     * successful initialization will do nothing.</p>
     *
     * @throws Exception if container startup or model pulling fails
     */
    public static synchronized void initializeContainer() throws Exception {
        if (!initialized) {
            initialized = true; // Mark attempted first — prevents retries after Docker detection failure
            String endpoint = "http://localhost:11434";

            // Check if Ollama is already running on the default port
            if (isOllamaRunning(endpoint)) {
                System.out.println("Using existing Ollama instance at " + endpoint);
                // Don't create a container, just use the existing instance
                ollama = null;
            } else {
                // Start a new container with Testcontainers
                // asCompatibleSubstituteFor is required when using a mirror image
                ollama = new OllamaContainer(DockerImageName.parse(OLLAMA_IMAGE)
                        .asCompatibleSubstituteFor("ollama/ollama"));
                ollama.start();
                endpoint = ollama.getEndpoint();

                // Pull models - this is a one-time operation
                ollama.execInContainer("ollama", "pull", MODEL_NAME);
                ollama.execInContainer("ollama", "pull", EMBEDDING_MODEL_NAME);
                System.out.println("Started new Ollama container at " + endpoint);
            }

            // Set system properties for tests to access
            System.setProperty("ollama.base.url", endpoint);
            System.setProperty("ollama.model.name", MODEL_NAME);
            System.setProperty("ollama.embedding.model.name", EMBEDDING_MODEL_NAME);

            serverVersion = fetchServerVersion(endpoint);

            embeddingDiagnostic = probeEmbeddingSupport(endpoint, EMBEDDING_MODEL_NAME);
            embeddingSupported = embeddingDiagnostic.startsWith("OK");
            if (!embeddingSupported) {
                System.err.println("Ollama at " + endpoint + " does not support embeddings — embedding tests will be skipped");
            }

            initialized = true;
        }
    }

    /**
     * Checks if Ollama is running at the given endpoint.
     *
     * <p>Performs a health check by sending a GET request to the {@code /api/tags}
     * endpoint with a 2-second timeout. This is used to detect existing Ollama
     * instances before attempting to start a new container.</p>
     *
     * @param endpoint the Ollama API endpoint URL (e.g., "http://localhost:11434")
     * @return {@code true} if Ollama responds with HTTP 200, {@code false} otherwise
     */
    private static boolean isOllamaRunning(String endpoint) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(endpoint + "/api/tags").toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (IOException | URISyntaxException e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String fetchServerVersion(String endpoint) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(endpoint + "/api/version").toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() != 200) {
                return "unknown";
            }
            try (java.io.InputStream in = conn.getInputStream()) {
                String body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // Response: {"version":"0.x.y"} — extract the value simply
                int start = body.indexOf("\"version\"");
                if (start < 0) {
                    return "unknown";
                }
                int colon = body.indexOf(':', start);
                int quote1 = body.indexOf('"', colon + 1);
                int quote2 = body.indexOf('"', quote1 + 1);
                if (quote1 < 0 || quote2 < 0) {
                    return "unknown";
                }
                return body.substring(quote1 + 1, quote2);
            }
        } catch (IOException | URISyntaxException e) {
            return "unknown";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Returns "OK (N dimensions)" on success, or a diagnostic string describing the failure.
    private static String probeEmbeddingSupport(String endpoint, String embeddingModel) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(endpoint + "/api/embeddings").toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            byte[] body = ("{\"model\":\"" + embeddingModel + "\",\"prompt\":\"test\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
                int status = conn.getResponseCode();
                if (status != 200) {
                    String errorDetail = "";
                    try (java.io.InputStream err = conn.getErrorStream()) {
                        if (err != null) {
                            errorDetail = " — " + new String(err.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                        }
                    } catch (IOException ignored) {}
                    return "HTTP " + status + errorDetail;
                }
            }
            try (InputStream in = conn.getInputStream()) {
                String responseBody = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // Response: {"embedding":[...]} — count comma-separated values to get dimensions
                int arrayStart = responseBody.indexOf('[');
                int arrayEnd = responseBody.lastIndexOf(']');
                if (arrayStart < 0 || arrayEnd <= arrayStart) {
                    return "OK (dimensions unknown)";
                }
                String values = responseBody.substring(arrayStart + 1, arrayEnd).trim();
                int dimensions = values.isEmpty() ? 0 : values.split(",").length;
                return "OK (" + dimensions + " dimensions)";
            }
        } catch (IOException | URISyntaxException e) {
            return "error: " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Returns the Ollama API endpoint URL.
     *
     * <p>If using a Testcontainers-managed instance, returns the dynamically assigned
     * endpoint. If using an existing Ollama instance, returns the default endpoint
     * {@code http://localhost:11434}.</p>
     *
     * @return the Ollama API endpoint URL
     */
    public static String getEndpoint() {
        return ollama != null ? ollama.getEndpoint() : "http://localhost:11434";
    }

    /**
     * Returns the name of the Ollama model used for testing.
     *
     * @return the model name ({@code llama3.2:1b})
     */
    public static String getModelName() {
        return MODEL_NAME;
    }

    /**
     * Returns the name of the Ollama model used for embedding tests.
     *
     * @return the embedding model name ({@code nomic-embed-text})
     */
    public static String getEmbeddingModelName() {
        return EMBEDDING_MODEL_NAME;
    }

    /**
     * Returns the Ollama server version as reported by the {@code /api/version} endpoint.
     *
     * @return the version string (e.g., {@code "0.24.0"}), or {@code "unknown"} if it could not be determined
     */
    public static String getServerVersion() {
        return serverVersion != null ? serverVersion : "unknown";
    }

    /**
     * Checks if the Ollama instance has been initialized.
     *
     * @return {@code true} if initialization completed successfully, {@code false} otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if Ollama is available for testing.
     *
     * <p>Ollama is considered available if either a local instance was detected on
     * port 11434 or a Testcontainers-managed instance was successfully started.</p>
     *
     * @return {@code true} if Ollama is available, {@code false} otherwise
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Checks if the Ollama instance supports the embeddings API.
     *
     * <p>Old Ollama versions (pre-0.1.30) required a {@code --embeddings} flag to enable
     * the {@code /api/embeddings} endpoint. This method probes the endpoint during
     * initialization so that embedding tests can skip gracefully on unsupported instances.</p>
     *
     * @return {@code true} if the embeddings endpoint responded successfully, {@code false} otherwise
     */
    public static boolean isEmbeddingSupported() {
        return embeddingSupported;
    }

    /**
     * Returns a diagnostic string describing the result of the embeddings endpoint probe.
     *
     * <p>Examples: {@code "OK (384 dimensions)"}, {@code "HTTP 404"}, {@code "error: Connection refused"}.</p>
     *
     * @return diagnostic string, or {@code "not probed"} if initialization has not run
     */
    public static String getEmbeddingDiagnostic() {
        return embeddingDiagnostic != null ? embeddingDiagnostic : "not probed";
    }
}
