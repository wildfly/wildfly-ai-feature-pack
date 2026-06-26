/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.enterprise.inject.spi.CDI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.wildfly.extension.mcp.api.MCPContextKey;
import org.wildfly.extension.mcp.api.MCPMessageContext;
import org.wildfly.extension.mcp.api.MCPMessageListener;
import org.wildfly.extension.mcp.api.MCPMethods;

import static org.wildfly.extension.mcp.MCPLogger.ROOT_LOGGER;

/**
 * {@link MCPMessageListener} implementation that records OpenTelemetry metrics and traces
 * for every MCP JSON-RPC message dispatched through the handler.
 * <p>
 * This class is only instantiated when the WildFly OpenTelemetry subsystem is available.
 * The {@link OpenTelemetry} instance is lazily obtained from the deployment's CDI container
 * on first use, using the deployment classloader to access the correct CDI context.
 * </p>
 */
public class OpenTelemetryMCPMessageListener implements MCPMessageListener {

    private static final String INSTRUMENTATION_NAME = "org.wildfly.extension.mcp";
    // Package-private so unit tests can reference these constants instead of recreating them.
    static final MCPContextKey<Span> SPAN_ATTR_KEY = MCPContextKey.of("otel.span");
    static final MCPContextKey<Scope> SCOPE_ATTR_KEY = MCPContextKey.of("otel.scope");

    private static final AttributeKey<String> METHOD_KEY = AttributeKey.stringKey("mcp.method.name");
    private static final AttributeKey<String> ERROR_TYPE_KEY = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> GEN_AI_OPERATION_KEY = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_TOOL_KEY = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> GEN_AI_PROMPT_KEY = AttributeKey.stringKey("gen_ai.prompt.name");
    private static final AttributeKey<String> MCP_PROTOCOL_VERSION_KEY = AttributeKey.stringKey("mcp.protocol.version");
    private static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.stringKey("mcp.session.id");
    private static final AttributeKey<String> REQUEST_ID_KEY = AttributeKey.stringKey("jsonrpc.request.id");
    private static final AttributeKey<String> RPC_STATUS_CODE_KEY = AttributeKey.stringKey("rpc.response.status_code");
    private static final AttributeKey<String> RESOURCE_URI_KEY = AttributeKey.stringKey("mcp.resource.uri");
    private static final AttributeKey<String> CLIENT_ADDRESS_KEY = AttributeKey.stringKey("client.address");
    private static final AttributeKey<Long> CLIENT_PORT_KEY = AttributeKey.longKey("client.port");
    private static final AttributeKey<String> NETWORK_TRANSPORT_KEY = AttributeKey.stringKey("network.transport");
    private static final AttributeKey<String> NETWORK_PROTOCOL_NAME_KEY = AttributeKey.stringKey("network.protocol.name");
    private static final AttributeKey<String> NETWORK_PROTOCOL_VERSION_KEY = AttributeKey.stringKey("network.protocol.version");
    private static final AttributeKey<String> JSONRPC_PROTOCOL_VERSION_KEY = AttributeKey.stringKey("jsonrpc.protocol.version");

    // Static transport values: MCP always runs over HTTP/TCP.
    private static final String JSONRPC_VERSION = "2.0";
    private static final String NETWORK_TRANSPORT = "tcp";
    private static final String NETWORK_PROTOCOL_NAME = "http";

    // Spec-recommended explicit bucket boundaries for MCP operation/session duration histograms.
    private static final List<Double> MCP_DURATION_BUCKETS = List.of(
            0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0);

    // Reusable getter for extracting W3C trace context from a Map<String, String> carrier.
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final ClassLoader deploymentClassLoader;
    private volatile LongCounter requestCounter;
    private volatile LongCounter errorCounter;
    // DoubleHistogram preserves sub-second precision; values are recorded in seconds as a double.
    private volatile DoubleHistogram operationDuration;
    private volatile DoubleHistogram sessionDuration;
    private volatile Tracer tracer;
    private volatile TextMapPropagator propagator;
    private volatile boolean initialized;
    private volatile boolean initializationFailed;
    // Package-private for testing: counts actual CDI initialization attempts (not fast-path checks).
    volatile int initAttemptCount;
    // Holds per-connection session start information from notifications/initialized until close.
    // Includes protocol version info so session duration metrics can carry those attributes.
    // Memory leak prevention: ConnectionManager guarantees onConnectionClosed() for every removal.
    private record SessionMeta(long startNanos, String mcpProtocolVersion, String networkProtocolVersion) {}
    private final ConcurrentHashMap<String, SessionMeta> sessionStartTimes = new ConcurrentHashMap<>();

    public OpenTelemetryMCPMessageListener(ClassLoader deploymentClassLoader) {
        this.deploymentClassLoader = deploymentClassLoader;
    }

    // For testing only — bypasses CDI lookup by accepting an already-configured OpenTelemetry instance.
    OpenTelemetryMCPMessageListener(OpenTelemetry openTelemetry) {
        this.deploymentClassLoader = null;
        initializeFrom(openTelemetry);
    }

    private void initializeFrom(OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);
        this.requestCounter = meter.counterBuilder("mcp.server.request.count")
                .setDescription("Number of MCP JSON-RPC requests processed")
                .build();
        this.errorCounter = meter.counterBuilder("mcp.server.error.count")
                .setDescription("Number of MCP JSON-RPC requests that resulted in an error")
                .build();
        // Values are recorded as doubles to preserve sub-second precision for fast operations.
        // Bucket advice follows the spec-recommended boundaries; the SDK may override them.
        this.operationDuration = meter.histogramBuilder("mcp.server.operation.duration")
                .setDescription("Duration of MCP JSON-RPC request handling")
                .setUnit("s")
                .setExplicitBucketBoundariesAdvice(MCP_DURATION_BUCKETS)
                .build();
        this.sessionDuration = meter.histogramBuilder("mcp.server.session.duration")
                .setDescription("Duration of MCP client sessions from initialization to close")
                .setUnit("s")
                .setExplicitBucketBoundariesAdvice(MCP_DURATION_BUCKETS)
                .build();
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.initialized = true;
    }

    private void ensureInitialized() {
        if (!initialized && !initializationFailed) {
            synchronized (this) {
                if (!initialized && !initializationFailed) {
                    initAttemptCount++;
                    Thread currentThread = Thread.currentThread();
                    ClassLoader previousCl = currentThread.getContextClassLoader();
                    try {
                        currentThread.setContextClassLoader(deploymentClassLoader);
                        initializeFrom(CDI.current().select(OpenTelemetry.class).get());
                    } catch (Exception e) {
                        ROOT_LOGGER.openTelemtryListenerInitializationFailure(e);
                        initializationFailed = true;
                    } finally {
                        currentThread.setContextClassLoader(previousCl);
                    }
                }
            }
        }
    }

    @Override
    public void onBeforeMessageDispatched(MCPMessageContext context) {
        ensureInitialized();
        if (!initialized) {
            return;
        }
        // Extract trace context from params._meta; fall back to ambient context.
        io.opentelemetry.context.Context ambientCtx = io.opentelemetry.context.Context.current();
        io.opentelemetry.context.Context parentCtx = ambientCtx;
        Map<String, String> headers = context.propagationHeaders();
        boolean usedPropagatedParent = false;
        if (headers != null && !headers.isEmpty()) {
            io.opentelemetry.context.Context extracted = propagator.extract(ambientCtx, headers, MAP_GETTER);
            if (Span.fromContext(extracted).getSpanContext().isValid()) {
                parentCtx = extracted;
                usedPropagatedParent = true;
            }
        }

        // Span name: "{method}" or "{method} {target}" when a tool/prompt name is known.
        String target = context.genAiTarget();
        String spanName = (target != null) ? context.method() + " " + target : context.method();

        SpanBuilder spanBuilder = tracer.spanBuilder(spanName)
                .setParent(parentCtx)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(METHOD_KEY, context.method())
                .setAttribute(SESSION_ID_KEY, context.connectionId())
                // Recommended: always-constant transport/protocol values for MCP-over-HTTP.
                .setAttribute(JSONRPC_PROTOCOL_VERSION_KEY, JSONRPC_VERSION)
                .setAttribute(NETWORK_TRANSPORT_KEY, NETWORK_TRANSPORT)
                .setAttribute(NETWORK_PROTOCOL_NAME_KEY, NETWORK_PROTOCOL_NAME);
        // jsonrpc.request.id MUST NOT be set for MCP notifications, which carry no id.
        String requestId = context.requestId();
        if (requestId != null && !requestId.isEmpty()) {
            spanBuilder.setAttribute(REQUEST_ID_KEY, requestId);
        }
        // Link the ambient transport span (e.g. HTTP server span) when we use a propagated remote parent.
        if (usedPropagatedParent) {
            SpanContext ambientSpanCtx = Span.fromContext(ambientCtx).getSpanContext();
            if (ambientSpanCtx.isValid()) {
                spanBuilder.addLink(ambientSpanCtx);
            }
        }

        if (MCPMethods.TOOLS_CALL.equals(context.method())) {
            spanBuilder.setAttribute(GEN_AI_OPERATION_KEY, "execute_tool");
            if (target != null) {
                spanBuilder.setAttribute(GEN_AI_TOOL_KEY, target);
            }
        } else if (MCPMethods.PROMPTS_GET.equals(context.method()) && target != null) {
            spanBuilder.setAttribute(GEN_AI_PROMPT_KEY, target);
        }

        String protoVersion = context.protocolVersion();
        if (protoVersion != null) {
            spanBuilder.setAttribute(MCP_PROTOCOL_VERSION_KEY, protoVersion);
        }
        // Recommended: dynamic transport attributes when available from the HTTP exchange.
        String networkProtocolVersion = context.networkProtocolVersion();
        if (networkProtocolVersion != null) {
            spanBuilder.setAttribute(NETWORK_PROTOCOL_VERSION_KEY, networkProtocolVersion);
        }
        String clientAddress = context.clientAddress();
        if (clientAddress != null) {
            spanBuilder.setAttribute(CLIENT_ADDRESS_KEY, clientAddress);
            int clientPort = context.clientPort();
            if (clientPort > 0) {
                spanBuilder.setAttribute(CLIENT_PORT_KEY, (long) clientPort);
            }
        }
        // Conditionally required: resource URI for resource methods.
        String resourceUri = context.resourceUri();
        if (resourceUri != null) {
            spanBuilder.setAttribute(RESOURCE_URI_KEY, resourceUri);
        }

        Span span = spanBuilder.startSpan();
        // makeCurrent() links this span as the active parent for any child spans created
        // during message dispatch; the Scope is closed in onAfterMessageDispatched/onError.
        Scope scope = span.makeCurrent();
        context.setAttribute(SPAN_ATTR_KEY, span);
        context.setAttribute(SCOPE_ATTR_KEY, scope);
    }

    @Override
    public void onAfterMessageDispatched(MCPMessageContext context) {
        if (!initialized) {
            return;
        }
        Attributes attrs = buildOperationAttrs(context, null);
        requestCounter.add(1, attrs);
        operationDuration.record(context.durationNanos() / 1_000_000_000.0, attrs);

        if (MCPMethods.NOTIFICATIONS_INITIALIZED.equals(context.method())) {
            sessionStartTimes.put(context.connectionId(),
                    new SessionMeta(context.startTimeNanos(), context.protocolVersion(), context.networkProtocolVersion()));
        } else if (MCPMethods.Q_CLOSE.equals(context.method())) {
            recordSessionDuration(context, null);
        }

        endSpanAndCloseScope(context);
    }

    @Override
    public void onError(MCPMessageContext context, Throwable error) {
        if (!initialized) {
            return;
        }
        String errorType = String.valueOf(context.errorCode());
        Attributes attrs = buildOperationAttrs(context, errorType);
        requestCounter.add(1, attrs);
        operationDuration.record(context.durationNanos() / 1_000_000_000.0, attrs);
        errorCounter.add(1, attrs);

        // Record session duration even if the close failed, to avoid leaking the entry.
        if (MCPMethods.Q_CLOSE.equals(context.method())) {
            recordSessionDuration(context, errorType);
        }

        Span span = context.getAttribute(SPAN_ATTR_KEY);
        if (span != null) {
            span.setAttribute(ERROR_TYPE_KEY, errorType);
            span.setAttribute(RPC_STATUS_CODE_KEY, errorType);
            span.setStatus(StatusCode.ERROR, context.errorMessage() != null ? context.errorMessage() : "error");
            if (error != null) {
                span.recordException(error);
            }
        }
        endSpanAndCloseScope(context);
    }

    @Override
    public void onConnectionClosed(String connectionId) {
        SessionMeta meta = sessionStartTimes.remove(connectionId);
        if (meta != null && initialized) {
            recordSessionDuration(connectionId, meta, null);
        }
    }

    private void recordSessionDuration(MCPMessageContext context, String errorType) {
        // Use computeIfPresent to atomically remove and record, preventing race conditions
        // between q/close and onConnectionClosed callbacks
        sessionStartTimes.computeIfPresent(context.connectionId(), (id, meta) -> {
            recordSessionDuration(id, meta, errorType);
            return null;
        });
    }

    private void recordSessionDuration(String connectionId, SessionMeta meta, String errorType) {
        long durationNanos = System.nanoTime() - meta.startNanos();
        AttributesBuilder builder = Attributes.builder()
                .put(SESSION_ID_KEY, connectionId)
                .put(JSONRPC_PROTOCOL_VERSION_KEY, JSONRPC_VERSION)
                .put(NETWORK_TRANSPORT_KEY, NETWORK_TRANSPORT)
                .put(NETWORK_PROTOCOL_NAME_KEY, NETWORK_PROTOCOL_NAME);
        if (errorType != null) {
            builder.put(ERROR_TYPE_KEY, errorType);
        }
        if (meta.mcpProtocolVersion() != null) {
            builder.put(MCP_PROTOCOL_VERSION_KEY, meta.mcpProtocolVersion());
        }
        if (meta.networkProtocolVersion() != null) {
            builder.put(NETWORK_PROTOCOL_VERSION_KEY, meta.networkProtocolVersion());
        }
        sessionDuration.record(durationNanos / 1_000_000_000.0, builder.build());
    }

    // Builds metric attributes for mcp.server.operation.duration, including gen_ai dimensions when applicable.
    // errorType is null for successful operations.
    private Attributes buildOperationAttrs(MCPMessageContext context, String errorType) {
        AttributesBuilder builder = Attributes.builder()
                .put(METHOD_KEY, context.method())
                .put(JSONRPC_PROTOCOL_VERSION_KEY, JSONRPC_VERSION)
                .put(NETWORK_TRANSPORT_KEY, NETWORK_TRANSPORT)
                .put(NETWORK_PROTOCOL_NAME_KEY, NETWORK_PROTOCOL_NAME);
        if (errorType != null) {
            builder.put(ERROR_TYPE_KEY, errorType);
            builder.put(RPC_STATUS_CODE_KEY, errorType);
        }
        String protoVersion = context.protocolVersion();
        if (protoVersion != null) {
            builder.put(MCP_PROTOCOL_VERSION_KEY, protoVersion);
        }
        String networkProtocolVersion = context.networkProtocolVersion();
        if (networkProtocolVersion != null) {
            builder.put(NETWORK_PROTOCOL_VERSION_KEY, networkProtocolVersion);
        }
        String target = context.genAiTarget();
        if (MCPMethods.TOOLS_CALL.equals(context.method())) {
            builder.put(GEN_AI_OPERATION_KEY, "execute_tool");
            if (target != null) {
                builder.put(GEN_AI_TOOL_KEY, target);
            }
        } else if (MCPMethods.PROMPTS_GET.equals(context.method()) && target != null) {
            builder.put(GEN_AI_PROMPT_KEY, target);
        }
        return builder.build();
    }

    // OTel spec: scope must be closed before span is ended to avoid leaving stale context on the thread.
    private void endSpanAndCloseScope(MCPMessageContext context) {
        Scope scope = context.getAttribute(SCOPE_ATTR_KEY);
        if (scope != null) {
            scope.close();
        }
        Span span = context.getAttribute(SPAN_ATTR_KEY);
        if (span != null) {
            span.end();
        }
    }
}
