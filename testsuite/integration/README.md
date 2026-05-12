# WildFly AI Integration Tests

This module contains integration tests for the WildFly AI Feature Pack using Arquillian and Testcontainers.

## Overview

The integration tests automatically manage containers using Testcontainers, eliminating the need for manual setup. The container lifecycle is fully automated:

1. **Container Initialization**: JUnit Platform `TestExecutionListener` implementations trigger container managers before tests run
2. **Automatic Detection**: Managers detect existing local instances or start new containers
3. **Dynamic Configuration**: Container endpoints are dynamically passed to WildFly via system properties
4. **Model Download**: On first Ollama start, the `llama3.2:1b` model is automatically pulled
5. **Automatic Cleanup**: Containers are stopped when the build finishes via JVM shutdown hooks

### Managed Containers

- **Ollama** (`OllamaContainerManager`): Chat and embedding model backend
- **LGTM** (`LgtmContainerManager`): Grafana observability stack for OpenTelemetry testing (optional, gracefully skipped if Docker unavailable)

## Running Tests

### From Maven

Run integration tests with the `integration-test` Maven profile:

```bash
mvn clean verify -Pintegration-test
```

### From IDE

Tests can be run directly from your IDE. The `OllamaContainerInitializer` ensures the Ollama container starts before any tests execute.

## Architecture

### Key Components

1. **OllamaContainerManager** (`org.wildfly.ai.test.container.OllamaContainerManager`)
   - Singleton manager for Ollama container lifecycle
   - Detects existing Ollama instances on `localhost:11434` or starts a new container
   - Sets system properties for test access
   - Registers shutdown hook to stop container when build finishes

2. **LgtmContainerManager** (`org.wildfly.ai.test.container.LgtmContainerManager`)
   - Singleton manager for Grafana LGTM (Loki, Grafana, Tempo, Mimir) observability stack
   - Detects existing Grafana on `localhost:3000` or starts new container
   - Gracefully degrades when Docker unavailable (OpenTelemetry tests skipped)
   - Registers shutdown hook for automatic cleanup

3. **OllamaContainerInitializer** (`org.wildfly.ai.test.OllamaContainerInitializer`)
   - JUnit Platform `TestExecutionListener` that triggers Ollama container initialization
   - Registered via `META-INF/services/org.junit.platform.launcher.TestExecutionListener`
   - Ensures container starts before Arquillian launches WildFly

4. **LgtmContainerInitializer** (`org.wildfly.ai.test.LgtmContainerInitializer`)
   - JUnit Platform `TestExecutionListener` that triggers LGTM container initialization
   - Reports availability status before tests run
   - OpenTelemetry tests skip automatically if LGTM unavailable

5. **Arquillian Configuration** (`src/test/resources/arquillian.xml`)
   - Configured to pass Ollama endpoint as system properties to WildFly
   - Properties: `org.wildfly.ai.ollama.chat.url`, `org.wildfly.ai.ollama.embedding.url`

### System Properties

**Ollama Properties** (set by `OllamaContainerManager`):
- `ollama.base.url`: Ollama API endpoint (e.g., `http://localhost:11434`)
- `ollama.model.name`: Model name (`llama3.2:1b`)

These are passed to WildFly via `arquillian.xml`:
- `org.wildfly.ai.ollama.chat.url`
- `org.wildfly.ai.ollama.embedding.url`
- `org.wildfly.ai.ollama.chat.model.name`
- `org.wildfly.ai.ollama.embedding.model.name`

**LGTM Properties** (set by `LgtmContainerManager`):
- `lgtm.grafana.url`: Grafana web UI (e.g., `http://localhost:3000`)
- `lgtm.otlp.endpoint`: OTLP HTTP endpoint for traces/metrics (e.g., `http://localhost:4318`)
- `lgtm.prometheus.url`: Prometheus API (e.g., `http://localhost:9090`)

## Test Categories

### Chat Model Tests
- **OllamaChatModelTestCase**: Tests basic chat model functionality
- **OllamaStreamingChatModelTestCase**: Tests streaming chat capabilities

### Embedding Model Tests
- **AllMiniLmL6V2EmbeddingModelTestCase**: Tests in-memory embedding model
- **OllamaEmbeddingModelTestCase**: Tests Ollama embedding model

### Storage and Retrieval Tests
- **InMemoryEmbeddingStoreTestCase**: Tests in-memory embedding store
- **EmbeddingStoreContentRetrieverTestCase**: Tests content retrieval functionality

### Chat Memory Tests
- **ChatMemoryProviderDeploymentTest**: Tests chat memory provider CDI injection and basic conversation memory

### Observable/OpenTelemetry Tests
- **ObservableChatModelTestCase**: Runs against `wildfly-observable` (with OTel). Verifies that `SpanChatModelListener` and `MetricsChatModelListener` are present in CDI when OpenTelemetry is active, and that the `observable=false` class-name filter correctly removes them.
- **NonObservableChatModelTestCase**: Runs against the default server (no OTel). Verifies via reflection that telemetry listeners are not baked into the model when `observable=false`.
- **OpenTelemetryIntegrationTestCase**: Tests LGTM observability stack infrastructure (skipped if Docker unavailable)

## Using Local Instances

For faster development iteration, you can run services locally instead of starting containers for each test run. Container managers automatically detect local instances:

### Ollama
```bash
# Start local Ollama
ollama serve

# Or with containers:
podman run -d -p 11434:11434 ollama/ollama
podman exec <container-name> ollama pull llama3.2:1b

# Run tests - will use existing instance
mvn verify -Pintegration-test
```

### LGTM (OpenTelemetry Testing)
```bash
# Start Grafana LGTM locally
docker run -d -p 3000:3000 -p 4318:4318 -p 9090:9090 \
  --name lgtm grafana/otel-lgtm:0.17.1

# Run tests - OpenTelemetry tests will use existing instance
mvn verify -Pintegration-test
```

Container managers will detect local instances and use them, providing faster test execution and persistent state between runs.

## Troubleshooting

### Container Not Starting

If the container fails to start, check:
- Docker/Podman is running: `podman ps` or `docker ps`
- No port conflicts on 11434 (Ollama), 3000 (Grafana), 4318 (OTLP), or 9090 (Prometheus)
- Testcontainers has access to the container runtime

### Tests Failing with Connection Errors

**Ollama:**
- Verify Ollama is running: `podman ps` or check `http://localhost:11434/api/tags`
- Check logs: `podman logs <container-name>`
- Ensure the model is downloaded: `ollama list` or `podman exec <container-name> ollama list`

**LGTM:**
- Check if LGTM is available: Look for initialization message in test output
- Verify Grafana is accessible: `curl http://localhost:3000`
- OpenTelemetry tests will skip automatically if LGTM unavailable

### OpenTelemetry Tests Skipped

This is normal when Docker is unavailable. The LGTM container manager gracefully degrades:

```
=================================================
LGTM not available - OpenTelemetry tests disabled
To enable: Start Grafana locally on port 3000
  or ensure Docker is available
=================================================
Tests run: 33, Failures: 0, Errors: 0, Skipped: 4
```

To enable OpenTelemetry tests, either:
- Install Docker/Podman and ensure it's running
- Start Grafana LGTM locally on port 3000

### Container Cleanup

Containers are **automatically stopped** when the build finishes via JVM shutdown hooks. You'll see:

```
Stopping Ollama container...
Ollama container stopped successfully
Stopping LGTM container...
LGTM container stopped successfully
```

To manually clean up containers:
```bash
# List testcontainers
docker ps -a | grep testcontainers

# Stop and remove
docker stop <container-name> && docker rm <container-name>
```

## Dependencies

- **JUnit 5**: Test framework
- **Arquillian**: Container testing framework
- **Testcontainers**: Container lifecycle management
- **WildFly Arquillian**: WildFly managed container adapter
- **AssertJ**: Fluent assertions

## Two-Server Architecture

The test suite provisions two WildFly servers to isolate observable from non-observable behavior:

| Container qualifier  | Build directory      | Provisioning file       | OpenTelemetry | Port offset |
|----------------------|----------------------|-------------------------|---------------|-------------|
| `wildfly-managed`    | `target/server`      | `provisioning.xml`      | No            | 0 (default) |
| `wildfly-observable` | `target/server-otel` | `provisioning-otel.xml` | Yes           | +100        |

Tests that need OpenTelemetry listeners in CDI must annotate their `@Deployment` with `@TargetsContainer("wildfly-observable")`. Tests without that annotation deploy to the default `wildfly-managed` container.

## Configuration Files

- `pom.xml`: Maven configuration with dependencies and plugins
- `src/test/resources/arquillian.xml`: Arquillian container configuration (defines both containers)
- `src/test/resources/provisioning.xml`: WildFly provisioning without OpenTelemetry layer
- `src/test/resources/provisioning-otel.xml`: WildFly provisioning with `opentelemetry` layer
- `extra-content/`: Additional server configuration files (CLI scripts, etc.)
