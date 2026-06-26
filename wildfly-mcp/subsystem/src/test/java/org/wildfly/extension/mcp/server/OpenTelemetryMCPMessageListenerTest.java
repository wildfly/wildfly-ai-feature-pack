/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.wildfly.extension.mcp.api.MCPConnection;
import org.wildfly.extension.mcp.api.MCPMethods;

/**
 * Unit tests for {@link OpenTelemetryMCPMessageListener}.
 *
 * <p>Tests that verify span/metric content use {@link TestInfra} — a minimal OTel SDK
 * wired with {@link InMemorySpanExporter} and {@link InMemoryMetricReader} so spans and
 * metric data-points can be inspected after each listener call.</p>
 *
 * <p>Tests that only verify that no exception is thrown (CDI-failure paths, boundary
 * conditions) continue to use {@link OpenTelemetry#noop()} for simplicity.</p>
 */
public class OpenTelemetryMCPMessageListenerTest {

    // --- helpers --------------------------------------------------------------

    private static MCPMessageContextImpl newContext(String method) {
        return new MCPMessageContextImpl(method, "conn-1", "req-1",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
    }

    /**
     * Minimal OTel SDK backed by in-memory exporters.
     * Create a fresh instance per test to avoid cross-test interference.
     */
    private static final class TestInfra {

        final OpenTelemetrySdk sdk;
        final InMemorySpanExporter spanExporter;
        final InMemoryMetricReader metricReader;

        TestInfra() {
            spanExporter = InMemorySpanExporter.create();
            metricReader = InMemoryMetricReader.create();
            sdk = OpenTelemetrySdk.builder()
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .setTracerProvider(SdkTracerProvider.builder()
                            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                            .build())
                    .setMeterProvider(SdkMeterProvider.builder()
                            .registerMetricReader(metricReader)
                            .build())
                    .build();
        }

        /** Returns the single finished span, failing if there is not exactly one. */
        SpanData finishedSpan() {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertEquals("expected exactly one finished span", 1, spans.size());
            return spans.get(0);
        }

        /** Returns the named metric, or {@code null} if not yet recorded. */
        MetricData findMetric(String name) {
            return metricReader.collectAllMetrics().stream()
                    .filter(m -> name.equals(m.getName()))
                    .findFirst()
                    .orElse(null);
        }

        /** Sums the point values for a long-sum (counter) metric. */
        long counterSum(String metricName) {
            MetricData m = findMetric(metricName);
            if (m == null) {
                return 0;
            }
            return m.getLongSumData().getPoints().stream()
                    .mapToLong(io.opentelemetry.sdk.metrics.data.LongPointData::getValue)
                    .sum();
        }

        /** Sums the counts for a histogram metric (number of recorded observations). */
        long histogramCount(String metricName) {
            MetricData m = findMetric(metricName);
            if (m == null) {
                return 0;
            }
            return m.getHistogramData().getPoints().stream()
                    .mapToLong(io.opentelemetry.sdk.metrics.data.HistogramPointData::getCount)
                    .sum();
        }

        /** True if any recorded point for {@code metricName} carries the given attribute key/value. */
        <T> boolean histogramHasPoint(String metricName, AttributeKey<T> key, T value) {
            MetricData m = findMetric(metricName);
            if (m == null) {
                return false;
            }
            return m.getHistogramData().getPoints().stream()
                    .anyMatch(p -> value.equals(p.getAttributes().get(key)));
        }
    }

    // --- span lifecycle -------------------------------------------------------

    @Test
    public void testNormalFlowCreatesSpanWithExpectedMetadata() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        assertNotNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SPAN_ATTR_KEY));
        assertNotNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SCOPE_ATTR_KEY));

        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("ping", span.getName());
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
        assertEquals("ping", span.getAttributes().get(AttributeKey.stringKey("mcp.method.name")));
        assertEquals("conn-1", span.getAttributes().get(AttributeKey.stringKey("mcp.session.id")));
        assertEquals("req-1", span.getAttributes().get(AttributeKey.stringKey("jsonrpc.request.id")));
    }

    @Test
    public void testAfterMessageEndsSpanAndClosesScope() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        assertNotNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SPAN_ATTR_KEY));
        assertNotNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SCOPE_ATTR_KEY));

        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        // Span must be finished and status must be UNSET (spec: do not set OK on success).
        SpanData span = infra.finishedSpan();
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    public void testErrorFlowSetsSpanStatusError() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/list");
        ctx.setErrorCode(-32601);
        ctx.setErrorMessage("Method not found");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(500_000L);
        listener.onError(ctx, new RuntimeException("test error"));

        SpanData span = infra.finishedSpan();
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("-32601", span.getAttributes().get(AttributeKey.stringKey("error.type")));
        assertEquals(1L, infra.counterSum("mcp.server.request.count"));
        assertEquals(1L, infra.counterSum("mcp.server.error.count"));
    }

    @Test
    public void testErrorFlowWithNullThrowableDoesNotThrow() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("initialize");
        ctx.setErrorCode(-32603);
        ctx.setErrorMessage("Internal error");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onError(ctx, null);

        SpanData span = infra.finishedSpan();
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("-32603", span.getAttributes().get(AttributeKey.stringKey("error.type")));
        assertEquals(1L, infra.counterSum("mcp.server.request.count"));
        assertEquals(1L, infra.counterSum("mcp.server.error.count"));
    }

    @Test
    public void testSpanStoredInContextAttributes() {
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(OpenTelemetry.noop());
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);

        assertNotNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SPAN_ATTR_KEY));
        assertNotNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SCOPE_ATTR_KEY));
    }

    @Test
    public void testMultipleMethodsTracked() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        String[] methods = {"ping", "tools/list", "tools/call", "initialize"};

        for (String method : methods) {
            MCPMessageContextImpl ctx = newContext(method);
            listener.onBeforeMessageDispatched(ctx);
            assertNotNull("span should be stored for method: " + method,
                    ctx.getAttribute(OpenTelemetryMCPMessageListener.SPAN_ATTR_KEY));
            assertNotNull("scope should be stored for method: " + method,
                    ctx.getAttribute(OpenTelemetryMCPMessageListener.SCOPE_ATTR_KEY));
            ctx.setDurationNanos(1_000_000L);
            listener.onAfterMessageDispatched(ctx);
        }

        List<SpanData> spans = infra.spanExporter.getFinishedSpanItems();
        assertEquals(methods.length, spans.size());
        for (int i = 0; i < methods.length; i++) {
            assertEquals(methods[i], spans.get(i).getName());
            assertEquals(SpanKind.SERVER, spans.get(i).getKind());
        }
    }

    @Test
    public void testToolsCallSetsGenAiOperationName() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/call");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("execute_tool", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    }

    @Test
    public void testNonToolCallDoesNotSetGenAiOperationName() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertNull(span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    }

    @Test
    public void testToolsCallWithTargetSetsGenAiToolNameAndSpanName() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/call");
        ctx.setGenAiTarget("Flights");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("tools/call Flights", span.getName());
        assertEquals("Flights", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
        assertEquals("execute_tool", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    }

    @Test
    public void testPromptsGetSetsGenAiPromptNameAndSpanName() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("prompts/get");
        ctx.setGenAiTarget("analyze-code");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("prompts/get analyze-code", span.getName());
        assertEquals("analyze-code", span.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt.name")));
        assertNull(span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    }

    @Test
    public void testProtocolVersionSetOnSpan() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/list");
        ctx.setProtocolVersion("2025-03-26");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("2025-03-26", span.getAttributes().get(AttributeKey.stringKey("mcp.protocol.version")));
    }

    @Test
    public void testToolsCallMetricAttributesIncludeGenAiDimensions() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/call");
        ctx.setGenAiTarget("Flights");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        assertTrue("operation duration should have a point with gen_ai.tool.name",
                infra.histogramHasPoint("mcp.server.operation.duration",
                        AttributeKey.stringKey("gen_ai.tool.name"), "Flights"));
        assertTrue("operation duration should have a point with gen_ai.operation.name",
                infra.histogramHasPoint("mcp.server.operation.duration",
                        AttributeKey.stringKey("gen_ai.operation.name"), "execute_tool"));
    }

    @Test
    public void testErrorSpanHasRpcResponseStatusCode() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/list");
        ctx.setErrorCode(-32601);
        ctx.setErrorMessage("Method not found");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(500_000L);
        listener.onError(ctx, null);

        SpanData span = infra.finishedSpan();
        assertEquals("-32601", span.getAttributes().get(AttributeKey.stringKey("rpc.response.status_code")));
    }

    @Test
    public void testW3cTraceContextExtractedFromPropagationHeaders() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        // A valid W3C traceparent: version=00, traceId, parentSpanId, flags=01 (sampled)
        String traceId   = "4bf92f3577b34da6a3ce929d0e0e4736";
        String parentId  = "00f067aa0ba902b7";
        ctx.setPropagationHeaders(Map.of("traceparent", "00-" + traceId + "-" + parentId + "-01"));

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(500_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertTrue("span parent context must be valid", span.getParentSpanContext().isValid());
        assertEquals(traceId,  span.getParentSpanContext().getTraceId());
        assertEquals(parentId, span.getParentSpanContext().getSpanId());
    }

    @Test
    public void testNotificationDoesNotSetJsonrpcRequestId() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        // Notifications have no id — MCPMessageHandler sets requestId to ""
        MCPMessageContextImpl ctx = new MCPMessageContextImpl(
                "notifications/initialized", "conn-1", "",
                MCPConnection.Status.INITIALIZING, System.nanoTime());

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(100_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertNull("jsonrpc.request.id must not be set for notifications",
                span.getAttributes().get(AttributeKey.stringKey("jsonrpc.request.id")));
    }

    @Test
    public void testAmbientContextLinkedWhenRemoteParentExtracted() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        // Create an ambient span (simulates an HTTP server span carrying its own trace)
        Span ambientSpan = infra.sdk.getTracer("test").spanBuilder("http-request").startSpan();
        try (Scope ambientScope = ambientSpan.makeCurrent()) {
            MCPMessageContextImpl ctx = newContext("tools/call");
            // Inject a different remote trace via params._meta
            String remoteTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
            String remoteParentId = "00f067aa0ba902b7";
            ctx.setPropagationHeaders(Map.of("traceparent",
                    "00-" + remoteTraceId + "-" + remoteParentId + "-01"));

            listener.onBeforeMessageDispatched(ctx);
            ctx.setDurationNanos(1_000_000L);
            listener.onAfterMessageDispatched(ctx);
        } finally {
            ambientSpan.end();
        }

        List<SpanData> spans = infra.spanExporter.getFinishedSpanItems();
        SpanData mcpSpan = spans.stream()
                .filter(s -> "tools/call".equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MCP span not found"));

        // Parent must be the remote MCP client context (from params._meta)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", mcpSpan.getParentSpanContext().getTraceId());

        // Ambient HTTP span must be recorded as a link
        assertFalse("MCP span should have a link to the ambient HTTP span", mcpSpan.getLinks().isEmpty());
        assertEquals(ambientSpan.getSpanContext().getTraceId(),
                mcpSpan.getLinks().get(0).getSpanContext().getTraceId());
        assertEquals(ambientSpan.getSpanContext().getSpanId(),
                mcpSpan.getLinks().get(0).getSpanContext().getSpanId());
    }

    // --- metrics --------------------------------------------------------------

    @Test
    public void testRequestCounterIncrementsOnSuccess() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        MCPMessageContextImpl ctx1 = newContext("ping");
        ctx1.setDurationNanos(500_000L);
        listener.onBeforeMessageDispatched(ctx1);
        listener.onAfterMessageDispatched(ctx1);

        MCPMessageContextImpl ctx2 = newContext("tools/list");
        ctx2.setDurationNanos(1_000_000L);
        listener.onBeforeMessageDispatched(ctx2);
        listener.onAfterMessageDispatched(ctx2);

        assertEquals(2L, infra.counterSum("mcp.server.request.count"));
        assertEquals(2L, infra.histogramCount("mcp.server.operation.duration"));
    }

    @Test
    public void testErrorCounterIncrementsOnError() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("unknown/method");
        ctx.setErrorCode(-32601);
        ctx.setErrorMessage("Method not found");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(200_000L);
        listener.onError(ctx, null);

        assertEquals(1L, infra.counterSum("mcp.server.error.count"));
        // The request counter also fires for error paths
        assertEquals(1L, infra.counterSum("mcp.server.request.count"));
    }

    @Test
    public void testErrorMetricHasRpcStatusCode() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/call");
        ctx.setErrorCode(-32601);
        ctx.setErrorMessage("Method not found");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(500_000L);
        listener.onError(ctx, null);

        assertTrue("operation duration metric must carry rpc.response.status_code on error",
                infra.histogramHasPoint("mcp.server.operation.duration",
                        AttributeKey.stringKey("rpc.response.status_code"), "-32601"));
    }

    // --- session duration -----------------------------------------------------

    @Test
    public void testSessionDurationRecordedOnQClose() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        MCPMessageContextImpl initCtx = new MCPMessageContextImpl(
                MCPMethods.NOTIFICATIONS_INITIALIZED, "conn-sess", "req-1",
                MCPConnection.Status.INITIALIZING, System.nanoTime());
        listener.onBeforeMessageDispatched(initCtx);
        initCtx.setDurationNanos(500_000L);
        listener.onAfterMessageDispatched(initCtx);

        MCPMessageContextImpl closeCtx = new MCPMessageContextImpl(
                MCPMethods.Q_CLOSE, "conn-sess", "req-2",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
        listener.onBeforeMessageDispatched(closeCtx);
        closeCtx.setDurationNanos(100_000L);
        listener.onAfterMessageDispatched(closeCtx);

        assertEquals(1L, infra.histogramCount("mcp.server.session.duration"));
    }

    @Test
    public void testSessionDurationRecordedOnQCloseError() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        MCPMessageContextImpl initCtx = new MCPMessageContextImpl(
                MCPMethods.NOTIFICATIONS_INITIALIZED, "conn-err", "req-1",
                MCPConnection.Status.INITIALIZING, System.nanoTime());
        listener.onBeforeMessageDispatched(initCtx);
        initCtx.setDurationNanos(500_000L);
        listener.onAfterMessageDispatched(initCtx);

        MCPMessageContextImpl closeCtx = new MCPMessageContextImpl(
                MCPMethods.Q_CLOSE, "conn-err", "req-2",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
        closeCtx.setErrorCode(-32603);
        closeCtx.setErrorMessage("Unable to close");
        listener.onBeforeMessageDispatched(closeCtx);
        closeCtx.setDurationNanos(100_000L);
        // Session duration should be recorded even when q/close fails.
        listener.onError(closeCtx, null);

        assertEquals(1L, infra.histogramCount("mcp.server.session.duration"));
    }

    @Test
    public void testSessionDurationHasErrorTypeOnErrorClose() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        MCPMessageContextImpl initCtx = new MCPMessageContextImpl(
                MCPMethods.NOTIFICATIONS_INITIALIZED, "conn-sess-err2", "req-1",
                MCPConnection.Status.INITIALIZING, System.nanoTime());
        listener.onBeforeMessageDispatched(initCtx);
        initCtx.setDurationNanos(500_000L);
        listener.onAfterMessageDispatched(initCtx);

        MCPMessageContextImpl closeCtx = new MCPMessageContextImpl(
                MCPMethods.Q_CLOSE, "conn-sess-err2", "req-2",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
        closeCtx.setErrorCode(-32603);
        closeCtx.setErrorMessage("Unable to close");
        listener.onBeforeMessageDispatched(closeCtx);
        closeCtx.setDurationNanos(100_000L);
        listener.onError(closeCtx, null);

        assertEquals(1L, infra.histogramCount("mcp.server.session.duration"));
        assertTrue("session duration must carry error.type when session ends with error",
                infra.histogramHasPoint("mcp.server.session.duration",
                        AttributeKey.stringKey("error.type"), "-32603"));
    }

    @Test
    public void testNoSessionDurationWithoutInitialization() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        // q/close without a prior notifications/initialized — session duration must not be recorded
        MCPMessageContextImpl closeCtx = new MCPMessageContextImpl(
                MCPMethods.Q_CLOSE, "conn-no-init", "req-1",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
        listener.onBeforeMessageDispatched(closeCtx);
        closeCtx.setDurationNanos(100_000L);
        listener.onAfterMessageDispatched(closeCtx);

        assertEquals("session duration must not be recorded without a prior notifications/initialized",
                0L, infra.histogramCount("mcp.server.session.duration"));
    }

    @Test
    public void testOnConnectionClosedCleansUpSessionStartTime() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        MCPMessageContextImpl initCtx = new MCPMessageContextImpl(
                MCPMethods.NOTIFICATIONS_INITIALIZED, "conn-closed", "req-1",
                MCPConnection.Status.INITIALIZING, System.nanoTime());
        listener.onBeforeMessageDispatched(initCtx);
        initCtx.setDurationNanos(500_000L);
        listener.onAfterMessageDispatched(initCtx);

        // Connection dropped without q/close — ConnectionManager fires onConnectionClosed
        listener.onConnectionClosed("conn-closed");

        // Session duration should have been recorded once (via onConnectionClosed)
        assertEquals(1L, infra.histogramCount("mcp.server.session.duration"));

        // After cleanup, a subsequent q/close must not record a second session duration entry.
        MCPMessageContextImpl closeCtx = new MCPMessageContextImpl(
                MCPMethods.Q_CLOSE, "conn-closed", "req-2",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
        listener.onBeforeMessageDispatched(closeCtx);
        closeCtx.setDurationNanos(100_000L);
        listener.onAfterMessageDispatched(closeCtx);

        assertEquals("second q/close should not add another session duration entry",
                1L, infra.histogramCount("mcp.server.session.duration"));
    }

    @Test
    public void testOnConnectionClosedWithoutInitializationIsNoOp() {
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(OpenTelemetry.noop());

        // No notifications/initialized was ever sent for this connection
        listener.onConnectionClosed("conn-never-initialized");
    }

    // --- new spec attributes -------------------------------------------------

    @Test
    public void testConstantNetworkAndJsonRpcAttributesAlwaysSetOnSpan() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("2.0", span.getAttributes().get(AttributeKey.stringKey("jsonrpc.protocol.version")));
        assertEquals("tcp", span.getAttributes().get(AttributeKey.stringKey("network.transport")));
        assertEquals("http", span.getAttributes().get(AttributeKey.stringKey("network.protocol.name")));
    }

    @Test
    public void testClientAddressAndPortSetOnSpanWhenAvailable() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/list");
        ctx.setClientAddress("192.168.1.42");
        ctx.setClientPort(54321);

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("192.168.1.42", span.getAttributes().get(AttributeKey.stringKey("client.address")));
        assertEquals(54321L, (long) span.getAttributes().get(AttributeKey.longKey("client.port")));
    }

    @Test
    public void testClientAddressNotSetWhenAbsent() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertNull(span.getAttributes().get(AttributeKey.stringKey("client.address")));
        assertNull(span.getAttributes().get(AttributeKey.longKey("client.port")));
    }

    @Test
    public void testNetworkProtocolVersionSetOnSpanWhenAvailable() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");
        ctx.setNetworkProtocolVersion("1.1");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("1.1", span.getAttributes().get(AttributeKey.stringKey("network.protocol.version")));
    }

    @Test
    public void testResourceUriSetOnSpanForResourcesRead() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("resources/read");
        ctx.setResourceUri("file:///data/report.txt");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertEquals("file:///data/report.txt", span.getAttributes().get(AttributeKey.stringKey("mcp.resource.uri")));
    }

    @Test
    public void testResourceUriNotSetWhenAbsent() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/list");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        SpanData span = infra.finishedSpan();
        assertNull(span.getAttributes().get(AttributeKey.stringKey("mcp.resource.uri")));
    }

    @Test
    public void testMcpProtocolVersionOnOperationDurationMetric() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("tools/list");
        ctx.setProtocolVersion("2025-03-26");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        assertTrue("operation duration metric must carry mcp.protocol.version",
                infra.histogramHasPoint("mcp.server.operation.duration",
                        AttributeKey.stringKey("mcp.protocol.version"), "2025-03-26"));
    }

    @Test
    public void testConstantNetworkAttributesOnOperationDurationMetric() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        assertTrue(infra.histogramHasPoint("mcp.server.operation.duration",
                AttributeKey.stringKey("jsonrpc.protocol.version"), "2.0"));
        assertTrue(infra.histogramHasPoint("mcp.server.operation.duration",
                AttributeKey.stringKey("network.transport"), "tcp"));
        assertTrue(infra.histogramHasPoint("mcp.server.operation.duration",
                AttributeKey.stringKey("network.protocol.name"), "http"));
    }

    @Test
    public void testConstantNetworkAttributesOnSessionDurationMetric() {
        TestInfra infra = new TestInfra();
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(infra.sdk);

        MCPMessageContextImpl initCtx = new MCPMessageContextImpl(
                MCPMethods.NOTIFICATIONS_INITIALIZED, "conn-netattr", "req-1",
                MCPConnection.Status.INITIALIZING, System.nanoTime());
        initCtx.setProtocolVersion("2025-03-26");
        initCtx.setNetworkProtocolVersion("2");
        listener.onBeforeMessageDispatched(initCtx);
        initCtx.setDurationNanos(500_000L);
        listener.onAfterMessageDispatched(initCtx);

        MCPMessageContextImpl closeCtx = new MCPMessageContextImpl(
                MCPMethods.Q_CLOSE, "conn-netattr", "req-2",
                MCPConnection.Status.IN_OPERATION, System.nanoTime());
        listener.onBeforeMessageDispatched(closeCtx);
        closeCtx.setDurationNanos(100_000L);
        listener.onAfterMessageDispatched(closeCtx);

        assertTrue(infra.histogramHasPoint("mcp.server.session.duration",
                AttributeKey.stringKey("jsonrpc.protocol.version"), "2.0"));
        assertTrue(infra.histogramHasPoint("mcp.server.session.duration",
                AttributeKey.stringKey("network.transport"), "tcp"));
        assertTrue(infra.histogramHasPoint("mcp.server.session.duration",
                AttributeKey.stringKey("network.protocol.name"), "http"));
        assertTrue(infra.histogramHasPoint("mcp.server.session.duration",
                AttributeKey.stringKey("mcp.protocol.version"), "2025-03-26"));
        assertTrue(infra.histogramHasPoint("mcp.server.session.duration",
                AttributeKey.stringKey("network.protocol.version"), "2"));
    }

    // --- CDI failure path (these use noop — CDI is unavailable in unit tests) ---

    @Test
    public void testInitializationFailureMakesListenerNoOp() {
        // Production constructor: CDI is not available in unit tests, so ensureInitialized() fails.
        // The listener must silently become a no-op rather than throwing on every message.
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(getClass().getClassLoader());
        MCPMessageContextImpl ctx = newContext("ping");

        listener.onBeforeMessageDispatched(ctx);
        ctx.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx);

        // No span/scope should have been stored since initialization failed
        assertNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SPAN_ATTR_KEY));
        assertNull(ctx.getAttribute(OpenTelemetryMCPMessageListener.SCOPE_ATTR_KEY));
    }

    @Test
    public void testInitializationFailureNotRetriedOnEveryMessage() {
        OpenTelemetryMCPMessageListener listener = new OpenTelemetryMCPMessageListener(getClass().getClassLoader());
        MCPMessageContextImpl ctx1 = newContext("ping");
        MCPMessageContextImpl ctx2 = newContext("tools/list");
        MCPMessageContextImpl ctx3 = newContext("tools/call");

        // First call triggers one CDI lookup attempt (which fails in unit test environment)
        listener.onBeforeMessageDispatched(ctx1);
        ctx1.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx1);

        // Subsequent calls must use the fast-path (initializationFailed == true), not retry CDI
        listener.onBeforeMessageDispatched(ctx2);
        ctx2.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx2);

        listener.onBeforeMessageDispatched(ctx3);
        ctx3.setDurationNanos(1_000_000L);
        listener.onAfterMessageDispatched(ctx3);

        assertEquals("CDI lookup must only be attempted once", 1, listener.initAttemptCount);
    }
}
