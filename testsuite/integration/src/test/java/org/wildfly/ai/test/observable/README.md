# Observable Chat Model Tests

This package contains tests for the observable configuration feature in WildFly AI chat models.

## Overview

The tests verify that when `observable` is set to `false`, the `GenAISpanChatModelListener` and `GenAIMetricsChatModelListener` from langchain4j-cdi are filtered out, while custom listeners are preserved.

## Test Classes

### 1. ObservableChatModelTestCase
Deploys to the **`wildfly-observable`** container (OpenTelemetry subsystem enabled, via `provisioning-otel.xml`).

**What it tests:**
- Both `GenAISpanChatModelListener` and `GenAIMetricsChatModelListener` are registered in CDI when the OpenTelemetry subsystem is active
- Applying the `observable=false` class-name filter leaves only custom listeners (simulates what `WildFlyLLMConfig` does at model creation time)
- Custom listeners like `TestChatModelListener` are injectable and discoverable
- Basic chat model functionality

**Note:** This test does NOT verify that deployment-scoped listeners receive events from subsystem-created chat models. Chat models are created during server startup before the test deployment is available, so deployment-scoped listeners are not wired to those model instances. The filtering logic itself is unit-tested in `WildFlyLLMConfigTest`.

**Prerequisites:**
- Ollama container running
- `wildfly-observable` server provisioned with `opentelemetry` layer

### 2. NonObservableChatModelTestCase
Deploys to the **default `wildfly-managed`** container (no OpenTelemetry subsystem, via `provisioning.xml`).

**What it tests:**
- When `observable=false`, telemetry listeners (`GenAISpanChatModelListener`, `GenAIMetricsChatModelListener`) are **not** baked into the model at construction time — verified via reflection on the model's internal listener list
- Basic CDI injection and listener discoverability still work without OTel
- Basic chat model functionality

**Note:** Tests that require OTel listeners to be present in CDI belong in `ObservableChatModelTestCase` (which targets the OTel-enabled server). `NonObservableChatModelTestCase` focuses on the reflection-based assertion that the model was built without telemetry listeners.

**Prerequisites:**
- Ollama container running
- Default `wildfly-managed` server (no OTel required)

### 3. OpenTelemetryIntegrationTestCase
Integration test for OpenTelemetry observability infrastructure.

**Test Execution:** `@RunAsClient` - runs on test client, not inside WildFly

**What it tests:**
- LGTM infrastructure availability
- Grafana accessibility
- Tempo (traces) endpoint accessibility
- Prometheus/Mimir (metrics) endpoint accessibility
- OTLP endpoint configuration

**What it does NOT test:**
- Actual trace/metrics collection from WildFly (would require OpenTelemetry subsystem configuration)
- Chat model observability integration (requires configured models)

**Prerequisites:**
- Docker running (optional - tests skip gracefully if unavailable)
- Grafana LGTM container running (managed by `LgtmContainerManager`)
- No WildFly configuration needed (tests run as client)

**LGTM Stack (managed by LgtmContainerManager):**
- Tempo for traces (OTLP HTTP on port 4318)
- Mimir for metrics (port 9090)
- Grafana for visualization (port 3000)
- Automatic detection of local Grafana instances
- **Graceful degradation** - Tests skip automatically if Docker unavailable
- **Automatic cleanup** - Container stopped when build finishes

**Note:** This test runs as client (`@RunAsClient`) because it's testing external infrastructure,
not the deployed application. This avoids the need to include container manager classes in the
deployment archive.

**Graceful Degradation:** If Docker is unavailable, the LGTM container manager initialization
will fail silently, and all OpenTelemetry integration tests will be skipped. The build continues
successfully with other tests.

### 3. TestChatModelListener
A custom `ChatModelListener` implementation used in tests to verify that custom listeners work correctly. Uses atomic counters to track invocations of `onRequest()`, `onResponse()`, and `onError()` methods.

## Configuration

### WildFly Configuration
The tests use CLI scripts to configure WildFly:

1. **configure-opentelemetry.cli** - Configures OpenTelemetry subsystem to send traces/metrics to LGTM

### Provisioning
Two provisioning files are used:

- **`provisioning.xml`** — default server (`target/server`): includes `ollama-chat-model` and standard WildFly layers. **No `opentelemetry` layer.** Used by `NonObservableChatModelTestCase` and all non-OTel tests.
- **`provisioning-otel.xml`** — OTel server (`target/server-otel`): adds the `opentelemetry` layer. Used by `ObservableChatModelTestCase` and `OpenTelemetryIntegrationTestCase`.

## Running the Tests

### Unit Tests (Fast)
```bash
# Run the WildFlyLLMConfig unit tests
mvn test -pl wildfly-ai/injection -Dtest=WildFlyLLMConfigTest
```

These tests verify the filtering logic directly without requiring a full WildFly deployment.

### Integration Tests (Full)
```bash
# Run all observable tests
mvn test -pl testsuite/integration -Dtest=Observable*

# Run specific test
mvn test -pl testsuite/integration -Dtest=ObservableChatModelTestCase

# Run OpenTelemetry integration test
mvn test -pl testsuite/integration -Dtest=OpenTelemetryIntegrationTestCase
```

### Environment Variables
```bash
# Configure Ollama (optional, defaults to localhost:11434)
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL_NAME=llama3.2:1b

# Configure OpenTelemetry endpoint (optional)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
```

## Test Architecture

### Listener Filtering Logic
Located in `WildFlyLLMConfig.createProducerFunction()`:

1. When `observable=true`: All ChatModelListener beans are collected
2. When `observable=false`: All listeners are collected, then filtered:
   - `GenAISpanChatModelListener` is excluded
   - `GenAIMetricsChatModelListener` is excluded  
   - Custom listeners are preserved

The filtering is done by checking the fully-qualified class name:
```java
private boolean isObservabilityListener(ChatModelListener listener) {
    String className = listener.getClass().getName();
    return className.equals("dev.langchain4j.cdi.telemetry.GenAISpanChatModelListener") ||
           className.equals("dev.langchain4j.cdi.telemetry.GenAIMetricsChatModelListener");
}
```

### Mock Listeners for Testing
The unit tests use mock implementations in the `dev.langchain4j.cdi.telemetry` package to simulate the actual langchain4j-cdi listeners:

- `dev.langchain4j.cdi.telemetry.GenAISpanChatModelListener` (mock)
- `dev.langchain4j.cdi.telemetry.GenAIMetricsChatModelListener` (mock)

These mocks have the correct fully-qualified class name so they match the filtering logic.

## Testing Custom Listeners with Subsystem Models

To integration test custom listeners that receive events from subsystem-created chat models, you need to:

1. **Package the listener as a WildFly module** (not in the test deployment)
2. **Add module dependency** in the AI subsystem module descriptor
3. **Deploy before subsystem starts** so the listener is available during bean creation

### Example Module Structure:
```
modules/system/layers/base/com/example/listeners/main/
├── module.xml
└── my-listener.jar
```

### Example module.xml:
```xml
<module xmlns="urn:jboss:module:1.9" name="com.example.listeners">
    <resources>
        <resource-root path="my-listener.jar"/>
    </resources>
    <dependencies>
        <module name="dev.langchain4j.cdi"/>
        <module name="jakarta.enterprise.cdi.api"/>
    </dependencies>
</module>
```

### Add dependency to AI injection module:
Edit `org/wildfly/extension/ai/injection/main/module.xml`:
```xml
<module name="com.example.listeners" optional="true"/>
```

This way, your listener will be available when the subsystem creates chat model beans, and will receive events properly.

## Troubleshooting

### Tests fail with "Container not started"
Ensure Docker is running and you have network access to pull container images:
- `ollama/ollama`
- `grafana/otel-lgtm:0.17.1`

### OpenTelemetry tests fail
Check that the OpenTelemetry subsystem is properly configured:
```bash
# Verify OpenTelemetry layer is in provisioning-otel.xml (NOT provisioning.xml)
cat testsuite/integration/src/test/resources/provisioning-otel.xml | grep opentelemetry

# Check server logs for OpenTelemetry initialization
tail -f testsuite/integration/target/server/standalone/log/server.log
```

### Listener filtering not working
Verify the `observable` attribute is properly set in the chat model configuration:
```bash
# Check generated standalone.xml
cat testsuite/integration/target/server/standalone/configuration/standalone.xml | grep observable
```

## Related Code

- `WildFlyLLMConfig.java` - Contains the listener filtering logic
- `AbstractChatModelProviderServiceConfigurator.java` - Service configurator that sets up observable models
- `WildFlyChatModelConfig.java` - Configuration interface with isObservable() method

## References

- [LangChain4j CDI Integration](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-cdi)
- [OpenTelemetry Documentation](https://opentelemetry.io/)
- [Grafana LGTM Container](https://github.com/grafana/docker-otel-lgtm)
