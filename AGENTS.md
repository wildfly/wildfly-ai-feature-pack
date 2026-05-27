# AGENTS.md

## Project overview

WildFly AI Feature Pack (`org.wildfly.generative-ai:wildfly-ai-feature-pack`) is a feature-pack that integrates AI capabilities into WildFly applications.
It provides Galleon layers for chat models, embedding models, embedding stores, content retrievers, chat memory, web search, MCP (Model Context Protocol) client/server, and WASM support.

The project is at **experimental** stability level. It targets **WildFly** and uses **LangChain4j** as the core AI library with **LangChain4j-CDI** for Jakarta CDI integration.

## Build

```bash
./mvnw clean install                    # full build + tests
./mvnw clean install -DskipTests=true   # skip tests
./mvnw clean install -Dtest=TestClass   # run a single test class
```

- **Java 17** is required (source/target/release). CI also tests on JDK 21 and 25.
- Uses the Maven wrapper (`./mvnw`); do not require a system Maven install.
- The `ai-feature-pack` module requires `install` (not just `package`) because downstream modules resolve it from the local repository.

## Module structure

```
wildfly-ai/           Core AI subsystem
  injection/            CDI injection support (chat models, embeddings, etc.)
  subsystem/            WildFly subsystem extension (resource definitions, parsers, operations)
wildfly-mcp/          Model Context Protocol support
  injection/            MCP CDI injection
  subsystem/            MCP subsystem extension
wildfly-wasm/         WebAssembly support (proof of concept)
  api/                  Public API (@WasmTool, WasmInvoker, etc.)
  injection/            WASM CDI injection
  subsystem/            WASM subsystem extension
bom/                  Bill of Materials POM
provision/            Provisioning resources and feature pack specs
testsuite/
  integration/          Arquillian integration tests (deploys to managed WildFly)
  mcp/                  MCP-specific tests
doc/                  Documentation (Glow layer docs)
```

## Code conventions

### License header

Every Java file must start with:

```java
/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
```

XML files use the comment equivalent.

### Style

- Follows WildFly / JBoss coding conventions (enforced via Checkstyle from `jboss-parent`).
- Checkstyle suppressions are in `checkstyle-suppressions.xml` at the root and in `wildfly-ai/`.
- No Spotless or auto-formatter is configured; match the style of surrounding code.

### Package structure

- `org.wildfly.extension.ai` — AI subsystem classes
- `org.wildfly.extension.ai.injection` — CDI producers and injection logic
- `org.wildfly.extension.ai.deployment` — deployment processors
- `org.wildfly.extension.mcp` — MCP subsystem classes
- `org.wildfly.extension.mcp.injection` — MCP injection
- `org.wildfly.wasm` — WASM subsystem and API

### WildFly subsystem patterns

Each subsystem follows the standard WildFly subsystem pattern:
- `*SubsystemModel` — subsystem resource definition
- `*SubsystemRegistrar` — registers the subsystem
- `*SubsystemSchema` — XML schema versions and parsing
- `*SubsystemTransformation` — model transformers between versions
- Resource definitions under the subsystem for each configurable element (e.g., `OllamaChatModelResource`, `OpenAIChatModelResource`)

When adding a new AI provider or resource type, follow the existing pattern in `wildfly-mcp/subsystem/`.

### Logging

- verify that new logging messages at or above `INFO` are using a `@Message` method from a `Logger` instead of hard-coding the message.
- **never** change the `id` of an `@Message` annotation in a `Logger`.

### Code generation

- Be concise.
- Reuse existing code instead of generating new similar code. Before creating a new class or utility, check if an equivalent already exists in the codebase.
- Follow the same code conventions as existing code. If an existing convention seems incorrect, suggest changes before making them.
- Every new Java file must include the license header.
- New subsystem resources must follow the established WildFly subsystem patterns (`*Resource`, `*Registrar`, `*Schema` classes).
- Do not add version numbers in generated code; dependency versions are managed centrally in the root `pom.xml` properties.

## Architecture

- **Galleon layers** are defined in `ai-feature-pack/src/main/resources/layers/standalone/`. Each layer has a `layer-spec.xml` that declares dependencies on other layers, Maven packages, and a feature spec referencing the subsystem resource (e.g., `<feature spec="subsystem.ai.openai-chat-model">`).
- **Subsystem resources** in `wildfly-ai/subsystem/` map one-to-one to Galleon layer feature specs. Adding a new provider means adding both the resource classes and the corresponding layer spec.
- **Experimental stability** is enforced at every level: subsystem schema namespace, feature pack build config, layer specs, module descriptors, and server startup (`--stability=experimental`). All new resources must use `Stability.EXPERIMENTAL`.
- **Schema versioning** uses enum constants (e.g., `AISubsystemSchema.VERSION_1_0`). When changing the subsystem XML structure, increment the schema version — never modify an existing version's parsing behavior.
- **`install` vs `package`**: The `ai-feature-pack` module resolves internal artifacts (`wildfly-ai-subsystem`, `wildfly-mcp-subsystem`, etc.) from the local Maven repository during `prepare-package`. Running `package` alone causes dependency resolution failures.
- **Logger message IDs** use project code prefixes with sequential numbering: `WFAI` (AI subsystem), `WFMCP` (MCP subsystem), `WFAIINJC` (AI injection), `WFMCPINJC` (MCP injection). New messages must use the next available ID in the sequence.

## Security

- **API keys** are passed via environment variables (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `GITHUB_API_KEY`, `MISTRAL_API_KEY`, `GROQ_API_KEY`) or subsystem `api-key` attributes. The `api-key` attribute is marked with `SensitiveTargetAccessConstraintDefinition.CREDENTIAL` — never weaken this constraint.
- **Database passwords** (Neo4j, etc.) use WildFly credential references (`credential-reference` attribute) backed by Elytron credential stores. Always use credential references rather than plain-text passwords in configuration.
- **MCP server authentication** uses bearer tokens via OIDC (Elytron `elytron-oidc-client` subsystem, Keycloak). MCP secure deployments must be configured with `bearer-only=true`.
- Never commit real API keys, tokens, or passwords. Test configurations must use environment variable expressions (e.g., `${env.OPENAI_API_KEY:test-key}`).
- Report security vulnerabilities to `security@wildfly.org` (see [SECURITY.md](SECURITY.md)).

## Boundaries

**Never do:**
- Change the `id` of an existing `@Message` annotation in a Logger class — IDs are permanent and referenced in documentation and support tools.
- Modify an existing schema version's parsing behavior — increment the version instead.
- Commit real API keys, credentials, or tokens.
- Weaken access constraints on credential or security-sensitive attributes.
- Remove or rename a Galleon layer that has been released — this breaks existing provisioning configurations.

**Ask first:**
- Adding a new subsystem or Galleon feature pack (as opposed to a new layer in an existing subsystem).
- Changing dependency versions of WildFly, LangChain4j, or other core libraries.
- Modifying the feature pack stability level.
- Altering the MCP or AI subsystem XML schema structure.

## Testing

- **Unit tests**: JUnit 5 subsystem parse/marshal tests (e.g., `AISubsystemTestCase`).
- **Integration tests**: Arquillian-based, deploy to a managed WildFly server provisioned with specific Galleon layers. Located in `testsuite/integration/`.
- **Container dependencies**: Integration tests use Testcontainers to manage Ollama (for LLM) and LGTM (for OpenTelemetry). These start automatically if Docker/Podman is available.
- Run a specific test: `./mvnw clean install -Dtest=OllamaChatModelTestCase`

## CI

GitHub Actions (`.github/workflows/ci.yml`):
- Triggers on push and pull request to all branches.
- Matrix: macOS, Ubuntu, Windows x JDK 17, 21, 25.
- Command: `./mvnw --batch-mode --no-transfer-progress clean install`

## Key dependencies

| Dependency | Purpose |
|---|---|
| WildFly | Application server |
| LangChain4j | AI model abstraction |
| LangChain4j-CDI | CDI integration for LangChain4j |
| MCP Java Annotations | MCP server annotation API |
| Chicory | Java WASM runtime |
| Extism SDK | WASM plugin SDK |
| Arquillian | Integration test container |
| Testcontainers | Docker container management for tests |

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

Common types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`. Use a scope when it clarifies which module or area is affected (e.g., `feat(mcp): add streamable transport support`).

Always ask if the commit is related to a GitHub issue. If that's the case, add `This fixes #<issue>` at the end of the commit message.

## Contributing

- Squash commits into one (multiple meaningful commits are acceptable for large changes).
- Ask to run `./mvnw clean install` before committing to verify the build and tests pass.
- Full contributing guidelines: [CONTRIBUTING.md](CONTRIBUTING.md)
