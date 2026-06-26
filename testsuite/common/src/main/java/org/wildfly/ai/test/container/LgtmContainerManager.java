/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.container;

import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.logging.Logger;

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
 * @see LgtmContainerInitializer
 */
public class LgtmContainerManager {

    private static final Logger LOG = Logger.getLogger(LgtmContainerManager.class.getName());
    private static final String LGTM_IMAGE = System.getProperty("lgtm.image", "mirror.gcr.io/grafana/otel-lgtm:0.17.1");
    private static final int GRAFANA_PORT = 3000;

    private static volatile LgtmStackContainer lgtmContainer;
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    static {
        try {
            initializeContainer();
            registerShutdownHook();
            available = true;
        } catch (Throwable e) {
            LOG.warning("LGTM initialization skipped: " + e.getMessage());
            LOG.warning("OpenTelemetry integration tests will be disabled");
            available = false;
        }
    }

    private static void registerShutdownHook() {
        ContainerLifecycleUtil.registerShutdownHook(lgtmContainer, "LGTM");
    }

    // Not synchronized: this method is only called from the static initializer above,
    // and the JVM already serializes class initialization per classloader.
    static void initializeContainer() throws Exception {
        if (!initialized) {
            String grafanaUrl = "http://localhost:" + GRAFANA_PORT;
            String otlpEndpoint = System.getenv().getOrDefault("LGTM_OTLP_ENDPOINT", "http://localhost:4317");
            String prometheusUrl = System.getenv().getOrDefault("LGTM_PROMETHEUS_URL", "http://localhost:9090");

            if (isGrafanaRunning(grafanaUrl)) {
                LOG.info("Using existing LGTM instance at " + grafanaUrl
                        + " (OTLP: " + otlpEndpoint + ", Prometheus: " + prometheusUrl + ")");
                lgtmContainer = null;
            } else {
                LOG.info("Starting LGTM container (this may take a few minutes)...");
                lgtmContainer = new LgtmStackContainer(DockerImageName.parse(LGTM_IMAGE)
                        .asCompatibleSubstituteFor("grafana/otel-lgtm"));
                lgtmContainer.start();

                grafanaUrl = lgtmContainer.getGrafanaHttpUrl();
                otlpEndpoint = lgtmContainer.getOtlpGrpcUrl();
                prometheusUrl = lgtmContainer.getPrometheusHttpUrl();

                LOG.info("Started LGTM container:");
                LOG.info("  Grafana:    " + grafanaUrl);
                LOG.info("  OTLP gRPC:  " + otlpEndpoint);
                LOG.info("  OTLP HTTP:  " + lgtmContainer.getOtlpHttpUrl());
                LOG.info("  Prometheus: " + prometheusUrl);
                LOG.info("  Tempo:      " + lgtmContainer.getTempoUrl());
                LOG.info("  Loki:       " + lgtmContainer.getLokiUrl());
            }

            System.setProperty("lgtm.grafana.url", grafanaUrl);
            System.setProperty("lgtm.otlp.endpoint", otlpEndpoint);
            System.setProperty("lgtm.prometheus.url", prometheusUrl);

            initialized = true;
        }
    }

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
            return responseCode == 200 || responseCode == 302;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String getGrafanaUrl() {
        return lgtmContainer != null ? lgtmContainer.getGrafanaHttpUrl() : "http://localhost:" + GRAFANA_PORT;
    }

    public static String getOtlpHttpEndpoint() {
        return lgtmContainer != null ? lgtmContainer.getOtlpHttpUrl() : "http://localhost:4318";
    }

    public static String getOtlpGrpcEndpoint() {
        return lgtmContainer != null ? lgtmContainer.getOtlpGrpcUrl() : System.getProperty("lgtm.otlp.endpoint");
    }

    public static String getPrometheusUrl() {
        return lgtmContainer != null ? lgtmContainer.getPrometheusHttpUrl() : System.getProperty("lgtm.prometheus.url");
    }

    public static String getTempoUrl() {
        return lgtmContainer != null ? lgtmContainer.getTempoUrl() : "http://localhost:3200";
    }

    public static String getLokiUrl() {
        return lgtmContainer != null ? lgtmContainer.getLokiUrl() : "http://localhost:3100";
    }

    public static LgtmStackContainer getContainer() {
        return lgtmContainer;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isContainerRunning() {
        return lgtmContainer != null && lgtmContainer.isRunning();
    }
}
