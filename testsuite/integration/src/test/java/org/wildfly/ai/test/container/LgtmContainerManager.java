package org.wildfly.ai.test.container;

import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Singleton manager for the Grafana LGTM (Loki, Grafana, Tempo, Mimir) container lifecycle.
 *
 * <p>This manager ensures a single LGTM container is shared across all tests
 * to improve performance and reduce resource usage. It intelligently detects if
 * a Grafana instance is already running on the default port (3000) and reuses it,
 * or starts a new Testcontainers-managed {@link LgtmStackContainer} instance if needed.</p>
 *
 * <p>The LGTM stack provides:</p>
 * <ul>
 *   <li>Grafana (port 3000) - Visualization and dashboards</li>
 *   <li>Tempo (port 3200) - Distributed tracing</li>
 *   <li>Loki (port 3100) - Log aggregation</li>
 *   <li>OTLP gRPC (port 4317) - OpenTelemetry collector gRPC endpoint</li>
 *   <li>OTLP HTTP (port 4318) - OpenTelemetry collector HTTP endpoint</li>
 *   <li>Prometheus/Mimir (port 9090) - Metrics storage</li>
 * </ul>
 *
 * <p><strong>Graceful Degradation:</strong></p>
 * <p>If Docker is unavailable, initialization fails silently and {@link #isAvailable()} returns
 * {@code false}. Tests should check availability using {@link #isAvailable()} before attempting
 * to use LGTM endpoints. OpenTelemetry integration tests automatically skip when LGTM is unavailable.</p>
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
 *   <li>{@code lgtm.grafana.url} - Grafana web UI URL</li>
 *   <li>{@code lgtm.otlp.endpoint} - OTLP gRPC endpoint for traces and metrics (port 4317)</li>
 *   <li>{@code lgtm.prometheus.url} - Prometheus API URL</li>
 * </ul>
 *
 * @see LgtmStackContainer
 * @see org.wildfly.ai.test.LgtmContainerInitializer
 */
public class LgtmContainerManager {

    private static final String LGTM_IMAGE = "mirror.gcr.io/grafana/otel-lgtm:0.17.1";
    private static final int GRAFANA_PORT = 3000;

    private static volatile LgtmStackContainer lgtmContainer;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    /**
     * Static initializer that ensures LGTM is ready before any tests run.
     *
     * <p>Performs container initialization and shutdown hook registration.
     * Unlike {@link OllamaContainerManager}, this initializer does not throw exceptions
     * if Docker is unavailable, allowing tests to run without OpenTelemetry infrastructure.</p>
     *
     * <p>On failure, sets {@link #available} to {@code false} and prints informational
     * message to stderr. Tests should check {@link #isAvailable()} before using LGTM endpoints.</p>
     */
    static {
        try {
            initializeContainer();
            registerShutdownHook();
            available = true;
        } catch (Throwable e) {
            System.err.println("LGTM initialization skipped: " + e.getMessage());
            System.err.println("OpenTelemetry integration tests will be disabled");
            available = false;
        }
    }

    /**
     * Registers a shutdown hook to stop the container when the JVM exits.
     * Only stops containers that were started by Testcontainers, not existing local instances.
     */
    private static void registerShutdownHook() {
        ContainerLifecycleUtil.registerShutdownHook(lgtmContainer, "LGTM");
    }

    /**
     * Initializes the LGTM container or detects an existing instance.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *   <li>Checks if Grafana is already running on http://localhost:3000</li>
     *   <li>If found, uses the existing instance to avoid port conflicts</li>
     *   <li>If not found, starts a new {@link LgtmStackContainer} managed by Testcontainers</li>
     *   <li>Waits for the full stack to be ready (log-based wait strategy)</li>
     *   <li>Sets system properties for test access</li>
     * </ol>
     *
     * <p>This method is thread-safe and idempotent - subsequent calls after
     * successful initialization will do nothing.</p>
     *
     * @throws Exception if container startup fails
     */
    public static synchronized void initializeContainer() throws Exception {
        if (!initialized) {
            String grafanaUrl = "http://localhost:" + GRAFANA_PORT;
            String otlpEndpoint = "http://localhost:4317";
            String prometheusUrl = "http://localhost:9090";

            if (isGrafanaRunning(grafanaUrl)) {
                System.out.println("Using existing LGTM instance at " + grafanaUrl);
                lgtmContainer = null;
            } else {
                System.out.println("Starting LGTM container (this may take a few minutes)...");
                // asCompatibleSubstituteFor is required when using a mirror image
                lgtmContainer = new LgtmStackContainer(DockerImageName.parse(LGTM_IMAGE)
                        .asCompatibleSubstituteFor("grafana/otel-lgtm"));
                lgtmContainer.start();

                grafanaUrl = lgtmContainer.getGrafanaHttpUrl();
                otlpEndpoint = lgtmContainer.getOtlpGrpcUrl();
                prometheusUrl = lgtmContainer.getPrometheusHttpUrl();

                System.out.println("Started LGTM container:");
                System.out.println("  Grafana:    " + grafanaUrl);
                System.out.println("  OTLP gRPC:  " + otlpEndpoint);
                System.out.println("  OTLP HTTP:  " + lgtmContainer.getOtlpHttpUrl());
                System.out.println("  Prometheus: " + prometheusUrl);
                System.out.println("  Tempo:      " + lgtmContainer.getTempoUrl());
                System.out.println("  Loki:       " + lgtmContainer.getLokiUrl());
            }

            System.setProperty("lgtm.grafana.url", grafanaUrl);
            System.setProperty("lgtm.otlp.endpoint", otlpEndpoint);
            System.setProperty("lgtm.prometheus.url", prometheusUrl);

            initialized = true;
        }
    }

    /**
     * Checks if Grafana is running at the given endpoint.
     *
     * <p>Performs a health check by sending a GET request to the root endpoint
     * with a 2-second timeout. This is used to detect existing LGTM/Grafana
     * instances before attempting to start a new container.</p>
     *
     * @param grafanaUrl the Grafana URL (e.g., "http://localhost:3000")
     * @return {@code true} if Grafana responds with HTTP 200 or 302, {@code false} otherwise
     */
    private static boolean isGrafanaRunning(String grafanaUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(grafanaUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setInstanceFollowRedirects(false);
            int responseCode = conn.getResponseCode();
            // Grafana returns 302 redirect to login page when running
            return responseCode == 200 || responseCode == 302;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Returns the Grafana web UI URL.
     *
     * <p>If using a Testcontainers-managed instance, returns the dynamically assigned URL.
     * If using an existing local instance, returns {@code http://localhost:3000}.</p>
     *
     * @return the Grafana URL
     */
    public static String getGrafanaUrl() {
        return lgtmContainer != null ? lgtmContainer.getGrafanaHttpUrl() : "http://localhost:" + GRAFANA_PORT;
    }

    /**
     * Returns the OTLP HTTP endpoint URL for sending traces and metrics.
     *
     * <p>Uses port 4318 (HTTP/Protobuf transport).</p>
     *
     * @return the OTLP HTTP endpoint URL
     */
    public static String getOtlpHttpEndpoint() {
        return lgtmContainer != null ? lgtmContainer.getOtlpHttpUrl() : "http://localhost:4318";
    }

    /**
     * Returns the OTLP gRPC endpoint URL for sending traces and metrics.
     *
     * @return the OTLP gRPC endpoint URL
     */
    public static String getOtlpGrpcEndpoint() {
        return lgtmContainer != null ? lgtmContainer.getOtlpGrpcUrl() : "http://localhost:4317";
    }

    /**
     * Returns the Prometheus API URL for querying metrics.
     *
     * @return the Prometheus URL
     */
    public static String getPrometheusUrl() {
        return lgtmContainer != null ? lgtmContainer.getPrometheusHttpUrl() : "http://localhost:9090";
    }

    /**
     * Returns the Tempo URL for querying traces.
     *
     * @return the Tempo URL
     */
    public static String getTempoUrl() {
        return lgtmContainer != null ? lgtmContainer.getTempoUrl() : "http://localhost:3200";
    }

    /**
     * Returns the Loki URL for querying logs.
     *
     * @return the Loki URL
     */
    public static String getLokiUrl() {
        return lgtmContainer != null ? lgtmContainer.getLokiUrl() : "http://localhost:3100";
    }

    /**
     * Returns the underlying {@link LgtmStackContainer} instance, or {@code null} if using a local instance.
     *
     * @return the LGTM container or {@code null}
     */
    public static LgtmStackContainer getContainer() {
        return lgtmContainer;
    }

    /**
     * Checks if the LGTM instance has been initialized.
     *
     * @return {@code true} if initialization completed successfully, {@code false} otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if LGTM is available for testing.
     *
     * <p>LGTM is considered available if either:</p>
     * <ul>
     *   <li>Grafana is running locally on port 3000</li>
     *   <li>A Testcontainers-managed LGTM instance was successfully started</li>
     * </ul>
     *
     * <p>Tests should check this before attempting to use LGTM endpoints.</p>
     *
     * @return {@code true} if LGTM is available, {@code false} otherwise
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Checks if a Testcontainers-managed instance is running.
     *
     * @return {@code true} if using a Testcontainers-managed LGTM instance
     */
    public static boolean isContainerRunning() {
        return lgtmContainer != null && lgtmContainer.isRunning();
    }
}
