/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.wildfly.ai.test.container.LgtmContainerManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests verifying that MCP message handling produces OpenTelemetry
 * traces and metrics when the OpenTelemetry subsystem is enabled.
 *
 * <p>Deploys to the {@code wildfly-observable} container (WildFly provisioned with
 * the {@code opentelemetry} Galleon layer) and sends MCP JSON-RPC messages.
 * After a brief flush delay, queries the LGTM stack through Grafana's datasource
 * proxy (since Tempo/Prometheus ports may not be directly accessible with Podman).</p>
 */
public class MCPOpenTelemetryIntegrationTestCase extends AbstractMCPIntegrationTestCase {

    private static final Logger LOG = Logger.getLogger(MCPOpenTelemetryIntegrationTestCase.class.getName());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String grafanaUrl;
    // datasource type -> UID
    private static final Map<String, String> datasourceUids = new HashMap<>();

    @Deployment(testable = false)
    @TargetsContainer("wildfly-observable")
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "mcp-otel-test.war")
                .addClass(TestMCPTool.class)
                .addClass(TestMCPTool.AddResult.class)
                .addClass(TestMCPPrompt.class)
                .addClass(TestMCPResource.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    // @BeforeAll may be non-static here because AbstractMCPIntegrationTestCase is annotated
    // with @TestInstance(Lifecycle.PER_CLASS), which JUnit 5 inherits into subclasses.
    @BeforeAll
    public void checkLgtmAndDiscoverDatasources() throws Exception {
        assumeTrue(LgtmContainerManager.isAvailable(),
                "LGTM observability stack not available - skipping OpenTelemetry MCP tests. "
                + "Start Grafana locally on port 3000 or ensure Docker is available.");

        grafanaUrl = LgtmContainerManager.getGrafanaUrl();

        LOG.info("=== OTel Configuration Diagnostic ===");
        LOG.info("  OTLP gRPC endpoint:       " + LgtmContainerManager.getOtlpGrpcEndpoint());
        LOG.info("  sys[lgtm.otlp.endpoint]:  " + System.getProperty("lgtm.otlp.endpoint"));
        LOG.info("  Grafana URL:              " + grafanaUrl);
        LOG.info("=====================================");

        HttpRequest dsRequest = HttpRequest.newBuilder()
                .uri(URI.create(grafanaUrl + "/api/datasources"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> dsResponse = HTTP_CLIENT.send(dsRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(dsResponse.statusCode()).as("Grafana datasources API should be accessible").isEqualTo(200);

        JsonArray datasources = Json.createReader(new StringReader(dsResponse.body())).readArray();
        for (int i = 0; i < datasources.size(); i++) {
            JsonObject ds = datasources.getJsonObject(i);
            datasourceUids.put(ds.getString("type", ""), ds.getString("uid"));
        }
    }

    @BeforeEach
    public void checkLgtm() {
        assumeTrue(LgtmContainerManager.isAvailable());
    }

    @Test
    public void testMcpSpansExportedToTempo() throws Exception {
        String tempoUid = datasourceUids.get("tempo");
        assertThat(tempoUid).as("Tempo datasource should be configured in Grafana").isNotNull();

        sendAndReceive("ping", null);
        sendAndReceive("tools/list", null);

        JsonObject callParams = Json.createObjectBuilder()
                .add("name", "echo")
                .add("arguments", Json.createObjectBuilder().add("message", "otel-test"))
                .build();
        sendAndReceive("tools/call", callParams);

        String searchUrl = grafanaUrl + "/api/datasources/proxy/uid/" + tempoUid
                + "/api/search?service.name=wildfly-mcp-test&limit=20";

        // Poll until traces appear in Tempo (OTel batch exporter flush may take a few seconds)
        JsonArray traces = pollUntilNonEmpty(searchUrl, "traces", 20_000, 2_000);

        assertThat(traces)
                .as("Should have exported at least one trace to Tempo")
                .isNotEmpty();
    }

    @Test
    public void testMcpMetricsExportedToPrometheus() throws Exception {
        String prometheusUid = datasourceUids.get("prometheus");
        assertThat(prometheusUid).as("Prometheus datasource should be configured in Grafana").isNotNull();

        sendAndReceive("ping", null);
        sendAndReceive("tools/list", null);

        String queryUrl = grafanaUrl + "/api/datasources/proxy/uid/" + prometheusUid
                + "/api/v1/query?query=mcp_server_request_count_total";

        // Poll until metrics appear in Prometheus (OTel metric export interval may take several seconds)
        JsonArray results = pollUntilNonEmpty(queryUrl, "data.result", 25_000, 2_000);

        assertThat(results)
                .as("Should have MCP request count metrics in Prometheus")
                .isNotEmpty();
    }

    @Test
    public void testMcpDurationMetricsExportedToPrometheus() throws Exception {
        String prometheusUid = datasourceUids.get("prometheus");
        assertThat(prometheusUid).as("Prometheus datasource should be configured in Grafana").isNotNull();

        sendAndReceive("ping", null);
        sendAndReceive("tools/list", null);

        String queryUrl = grafanaUrl + "/api/datasources/proxy/uid/" + prometheusUid
                + "/api/v1/query?query=mcp_server_operation_duration_seconds_count";

        JsonArray results = pollUntilNonEmpty(queryUrl, "data.result", 25_000, 2_000);

        assertThat(results)
                .as("Should have MCP request duration metrics in Prometheus")
                .isNotEmpty();
    }

    @Test
    public void testMcpErrorCountMetricsExportedToPrometheus() throws Exception {
        String prometheusUid = datasourceUids.get("prometheus");
        assertThat(prometheusUid).as("Prometheus datasource should be configured in Grafana").isNotNull();

        // Trigger an error by sending an unknown method after initialization
        sendAndReceive("unknown/method", null);

        String queryUrl = grafanaUrl + "/api/datasources/proxy/uid/" + prometheusUid
                + "/api/v1/query?query=mcp_server_error_count_total";

        JsonArray results = pollUntilNonEmpty(queryUrl, "data.result", 25_000, 2_000);

        assertThat(results)
                .as("Should have MCP error count metrics in Prometheus after sending an unknown method")
                .isNotEmpty();
    }

    @Test
    public void testMcpSessionDurationMetricsExportedToPrometheus() throws Exception {
        String prometheusUid = datasourceUids.get("prometheus");
        assertThat(prometheusUid).as("Prometheus datasource should be configured in Grafana").isNotNull();

        openAndCloseSession();

        String queryUrl = grafanaUrl + "/api/datasources/proxy/uid/" + prometheusUid
                + "/api/v1/query?query=mcp_server_session_duration_seconds_count";

        JsonArray results = pollUntilNonEmpty(queryUrl, "data.result", 25_000, 2_000);

        assertThat(results)
                .as("Should have MCP session duration metrics in Prometheus after closing a session")
                .isNotEmpty();
    }

    /**
     * Opens a dedicated MCP session and closes it via {@code q/close}, so the server
     * records a {@code session_duration} metric without disturbing the shared SSE session
     * used by other tests in this class.
     *
     * <p>Keeps the SSE response body open in a background drain thread so the server
     * can write the initialize response, waits for that event, then completes the
     * MCP handshake ({@code notifications/initialized}) before sending {@code q/close}.</p>
     */
    private void openAndCloseSession() throws Exception {
        URI streamUri = deploymentUrl.toURI().resolve("stream");
        long initId = nextId.getAndIncrement();

        String initMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", initId)
                .add("method", "initialize")
                .add("params", Json.createObjectBuilder()
                        .add("protocolVersion", "2025-03-26")
                        .add("clientInfo", Json.createObjectBuilder()
                                .add("name", "otel-session-test")
                                .add("version", "1.0.0"))
                        .add("capabilities", Json.createObjectBuilder()))
                .build().toString();

        HttpRequest initReq = HttpRequest.newBuilder()
                .uri(streamUri)
                .POST(HttpRequest.BodyPublishers.ofString(initMessage))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<InputStream> initResp = HTTP_CLIENT.send(initReq, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(initResp.statusCode()).as("Dedicated session initialize should return 200").isEqualTo(200);
        String dedicatedSessionId = initResp.headers().firstValue("mcp-session-id").orElse(null);
        assertThat(dedicatedSessionId).as("Dedicated session should return a session ID").isNotNull();

        // Drain the SSE body in the background so the server can write responses.
        // Signal when the first data event (initialize response) arrives.
        CompletableFuture<Void> initReceived = new CompletableFuture<>();
        Thread drainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(initResp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:") && !initReceived.isDone()) {
                        initReceived.complete(null);
                    }
                }
            } catch (Exception ignored) {}
        }, "otel-session-drain");
        drainThread.setDaemon(true);
        drainThread.start();

        initReceived.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        HTTP_CLIENT.send(HttpRequest.newBuilder()
                .uri(streamUri)
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("mcp-session-id", dedicatedSessionId)
                .timeout(Duration.ofSeconds(10))
                .build(), HttpResponse.BodyHandlers.discarding());

        String closeMessage = Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", nextId.getAndIncrement())
                .add("method", "q/close")
                .build().toString();

        HTTP_CLIENT.send(HttpRequest.newBuilder()
                .uri(streamUri)
                .POST(HttpRequest.BodyPublishers.ofString(closeMessage))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("mcp-session-id", dedicatedSessionId)
                .timeout(Duration.ofSeconds(10))
                .build(), HttpResponse.BodyHandlers.discarding());

        drainThread.interrupt();
        initResp.body().close();
    }

    private HttpResponse<String> httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Polls {@code url} every {@code intervalMs} until the JSON array at {@code arrayPath} is
     * non-empty, or {@code timeoutMs} elapses. {@code arrayPath} supports one level of nesting
     * with a dot separator (e.g. {@code "data.result"} or {@code "traces"}).
     * Returns an empty (never null) array if the timeout expires before data appears.
     */
    private JsonArray pollUntilNonEmpty(String url, String arrayPath, long timeoutMs, long intervalMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = httpGet(url);
            if (response.statusCode() == 200) {
                JsonObject body = Json.createReader(new StringReader(response.body())).readObject();
                JsonArray result = extractArray(body, arrayPath);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
            Thread.sleep(intervalMs);
        }
        return Json.createArrayBuilder().build();
    }

    private static JsonArray extractArray(JsonObject root, String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            return root.getJsonArray(path);
        }
        JsonObject nested = root.getJsonObject(path.substring(0, dot));
        return nested != null ? nested.getJsonArray(path.substring(dot + 1)) : null;
    }
}
