# Developing MCP Tools — Advanced API Guide

This guide covers the WildFly AI Feature Pack APIs for building MCP tools that go beyond
simple request/response: inspecting client capabilities, passing out-of-band context via
HTTP headers, and implementing multi-round tool interactions.

All examples assume a CDI bean with methods annotated with `@org.mcpjava.server.tools.Tool`.

## ClientCapabilities

`org.wildfly.mcp.api.ClientCapabilities` lets a tool inspect which optional MCP features
the connected client declared during initialization (e.g. `elicitation`, `sampling`, `roots`).

### API

| Method | Description |
|--------|-------------|
| `boolean hasCapability(String capability)` | Returns `true` if the client declared the named capability |
| `void requireCapability(String capability)` | Throws `MissingCapabilityException` if the capability is absent |

### Usage

Inject `ClientCapabilities` as a tool method parameter — the framework resolves it
automatically (it does not appear in the tool's input schema).

```java
import org.wildfly.mcp.api.ClientCapabilities;

@Tool(name = "smart_tool", description = "Adapts behavior to client capabilities")
Object smartTool(ClientCapabilities capabilities,
                 @ToolArg(description = "User input") String input) {
    if (capabilities.hasCapability("sampling")) {
        // use LLM sampling to enhance the response
    }
    return ToolResponse.ofText("result");
}
```

Use `requireCapability()` when a tool cannot function without a specific feature — the
server returns an error to the client automatically:

```java
@Tool(name = "requires_elicitation", description = "Tool that must elicit input")
Object elicitingTool(ClientCapabilities capabilities, InputResponses input) {
    capabilities.requireCapability("elicitation");
    // safe to send elicitation requests from here on
}
```

### Combining with InputRequiredResult

When building `InputRequiredResult` responses, check capabilities before adding
input requests to avoid requesting features the client does not support:

```java
@Tool(name = "capability_aware", description = "Only requests supported input types")
Object capabilityAwareTool(InputResponses input, ClientCapabilities capabilities) {
    if (input.hasResponses()) {
        return ToolResponse.ofText("Done");
    }
    InputRequiredResult.Builder builder = InputRequiredResult.builder();
    if (capabilities.hasCapability("elicitation")) {
        builder.addElicitation("confirm", "Please confirm", schema);
    }
    if (capabilities.hasCapability("sampling")) {
        builder.addSampling("summary", messages, 200);
    }
    return builder.build();
}
```

## McpHeader

`org.wildfly.mcp.api.McpHeader` is a parameter annotation that injects a value from
an MCP transport header into a tool method parameter. This enables passing out-of-band
context (authentication tokens, tenant IDs, locale preferences) without polluting the
tool's argument schema.

### Header Resolution

Values are extracted from two sources, in order of precedence:

1. HTTP request header: `x-mcp-header-<name>` (e.g. `x-mcp-header-language`)
2. JSON-RPC payload: `params._meta.headers.<name>`

The HTTP header takes precedence when both are present.

### Annotation Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | `String` | — | The header name (without the `x-mcp-header-` prefix) |
| `required` | `boolean` | `false` | When `true`, a missing header returns a `-32602 InvalidParams` error |

### Usage

```java
import org.wildfly.mcp.api.McpHeader;

@Tool(name = "greet", description = "Greets in the requested language")
String greet(@McpHeader("language") String language,
             @ToolArg(description = "Name to greet") String name) {
    String lang = language != null ? language : "en";
    return switch (lang) {
        case "fr" -> "Bonjour, " + name;
        case "es" -> "Hola, " + name;
        default -> "Hello, " + name;
    };
}
```

A required header that rejects the request when missing:

```java
@Tool(name = "secure_op", description = "Requires an auth token")
String secureOp(@McpHeader(value = "auth-token", required = true) String token) {
    // token is guaranteed non-null here
    return "Authenticated: " + token;
}
```

Multiple headers can be combined with regular tool arguments:

```java
@Tool(name = "multi_header", description = "Uses multiple headers")
String multiHeader(@McpHeader("token") String token,
                   @ToolArg(description = "Input data") String data,
                   @McpHeader("tenant-id") String tenantId) {
    return "tenant=" + tenantId + " data=" + data;
}
```

### Sending Headers from a Client

Over HTTP, set the prefixed header on the request:

```
POST /mcp HTTP/1.1
x-mcp-header-language: fr
Content-Type: application/json
```

In the JSON-RPC payload, headers go under `params._meta.headers`:

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 1,
  "params": {
    "name": "greet",
    "arguments": { "name": "Alice" },
    "_meta": {
      "headers": { "language": "fr" }
    }
  }
}
```

## Multi-Round Tool Results (MRTR)

The MRTR flow (SEP-2322) lets a tool pause execution to request additional input from
the client — user elicitation, LLM sampling, or a roots listing — then resume with
the responses. This is implemented with two APIs:

- **`InputRequiredResult`** — returned by the tool to request input
- **`InputResponses`** — injected into the tool on the follow-up call with the client's answers

### How It Works

```
Client                         Server (Tool)
  │                                │
  │  tools/call {name, args}       │
  │ ──────────────────────────────>│
  │                                │  (first call: no responses)
  │  resultType: input_required    │
  │  inputRequests: {...}          │
  │  requestState: "opaque"        │
  │ <──────────────────────────────│
  │                                │
  │  tools/call {name, args,       │
  │    inputResponses, requestState}│
  │ ──────────────────────────────>│
  │                                │  (follow-up: has responses)
  │  resultType: ...               │
  │  content: [final result]       │
  │ <──────────────────────────────│
```

### InputResponses

`org.wildfly.mcp.api.tool.InputResponses` is injected as a tool parameter. It provides
access to the client's responses from the previous round.

| Method | Description |
|--------|-------------|
| `boolean hasResponses()` | `true` if the client sent `inputResponses` in this call |
| `Map<String, JsonObject> responses()` | The responses keyed by the request key from `InputRequiredResult` |
| `String requestState()` | The `requestState` string echoed back by the client, or `null` |

### InputRequiredResult

`org.wildfly.mcp.api.tool.InputRequiredResult` is returned by the tool to signal that
more input is needed. Use the builder to add one or more input requests:

| Builder Method | Description |
|----------------|-------------|
| `addElicitation(key, message, schema)` | Request user input via `elicitation/create` |
| `addSampling(key, messages, maxTokens)` | Request LLM completion via `sampling/createMessage` |
| `addRootsList(key)` | Request the client's root URIs via `roots/list` |
| `addInputRequest(key, method, params)` | Add a raw input request with any method |
| `requestState(state)` | Set opaque server state to be echoed back in the next round |

### Basic Example — Single Elicitation

```java
import org.wildfly.mcp.api.tool.InputRequiredResult;
import org.wildfly.mcp.api.tool.InputResponses;

@Tool(name = "confirm_delete", description = "Asks for confirmation before deleting")
Object confirmDelete(@ToolArg(description = "Item to delete") String item,
                     InputResponses input) {
    if (!input.hasResponses()) {
        // First call: ask the user to confirm
        return InputRequiredResult.builder()
                .addElicitation("confirm", "Are you sure you want to delete '" + item + "'?",
                        Json.createObjectBuilder()
                                .add("type", "object")
                                .add("properties", Json.createObjectBuilder()
                                        .add("ok", Json.createObjectBuilder()
                                                .add("type", "boolean")))
                                .add("required", Json.createArrayBuilder().add("ok")))
                .build();
    }
    // Follow-up: user has responded
    JsonObject confirmResponse = input.responses().get("confirm");
    boolean confirmed = confirmResponse.getJsonObject("content")
            .getBoolean("ok", false);
    if (confirmed) {
        // perform deletion
        return ToolResponse.ofText("Deleted: " + item);
    }
    return ToolResponse.ofText("Deletion cancelled");
}
```

### Using requestState

`requestState` is an opaque string that the server sends to the client and the client
echoes back on the next call. Use it to carry context between rounds without server-side
session storage.

When `request-state-secret` is configured on the MCP subsystem, the server HMAC-signs the
`requestState` to prevent tampering. **Without this attribute, unsigned `requestState`
tokens are rejected by default.** See [SECURITY.md](../SECURITY.md) for deployment guidance.

```java
@Tool(name = "stateful_tool", description = "Uses requestState across rounds")
Object statefulTool(InputResponses input) {
    if (!input.hasResponses()) {
        return InputRequiredResult.builder()
                .addElicitation("step1", "What is your name?",
                        requiredStringSchema("name"))
                .requestState("step-1")
                .build();
    }
    String state = input.requestState();
    if ("step-1".equals(state)) {
        // Second round: ask another question
        return InputRequiredResult.builder()
                .addElicitation("step2", "What is your favorite color?",
                        requiredStringSchema("color"))
                .requestState("step-2")
                .build();
    }
    // Final round
    return ToolResponse.ofText("Multi-round complete");
}
```

### Multiple Input Types in One Round

A single `InputRequiredResult` can combine elicitation, sampling, and roots listing:

```java
@Tool(name = "multi_input", description = "Requests multiple input types at once")
Object multiInput(InputResponses input) {
    if (input.hasResponses()) {
        // All three responses arrive together
        JsonObject name = input.responses().get("elicit_name");
        JsonObject greeting = input.responses().get("sample_greeting");
        JsonObject roots = input.responses().get("list_roots");
        return ToolResponse.ofText("All inputs received");
    }
    return InputRequiredResult.builder()
            .addElicitation("elicit_name", "Please provide your name",
                    requiredStringSchema("name"))
            .addSampling("sample_greeting",
                    Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add("role", "user")
                                    .add("content", Json.createObjectBuilder()
                                            .add("type", "text")
                                            .add("text", "Generate a greeting"))),
                    100)
            .addRootsList("list_roots")
            .requestState("multi-input-state")
            .build();
}
```

### Return Type

Tools using MRTR must declare their return type as `Object` since they may return either
an `InputRequiredResult` (to request more input) or a `ToolResponse` / `String` / other
final result type.

### MRTR in Prompts

The same `InputResponses` / `InputRequiredResult` pattern works in `@Prompt` methods:

```java
@Prompt(name = "contextual_prompt", description = "Asks for context before generating")
Object contextualPrompt(InputResponses input) {
    if (input.hasResponses()) {
        return PromptResponse.of(Role.USER,
                TextContent.of("Prompt completed with input."));
    }
    return InputRequiredResult.builder()
            .addElicitation("context_input", "Please provide context",
                    Json.createObjectBuilder()
                            .add("type", "object")
                            .add("properties", Json.createObjectBuilder()
                                    .add("context", Json.createObjectBuilder()
                                            .add("type", "string")))
                            .add("required", Json.createArrayBuilder().add("context")))
            .build();
}
```

## Combining All APIs

A tool can use all four APIs together:

```java
@Tool(name = "full_featured", description = "Uses headers, capabilities, and MRTR")
Object fullFeatured(@McpHeader("tenant") String tenant,
                    @ToolArg(description = "Action to perform") String action,
                    ClientCapabilities capabilities,
                    InputResponses input) {
    if (input.hasResponses()) {
        return ToolResponse.ofText("Completed for tenant " + tenant);
    }
    // Only request elicitation if the client supports it
    if (capabilities.hasCapability("elicitation")) {
        return InputRequiredResult.builder()
                .addElicitation("confirm", "Confirm action: " + action,
                        Json.createObjectBuilder()
                                .add("type", "object")
                                .add("properties", Json.createObjectBuilder()
                                        .add("ok", Json.createObjectBuilder()
                                                .add("type", "boolean")))
                                .add("required", Json.createArrayBuilder().add("ok")))
                .requestState(tenant + ":" + action)
                .build();
    }
    // Client doesn't support elicitation — proceed directly
    return ToolResponse.ofText("Executed " + action + " for tenant " + tenant);
}
```

## Dependency

Add the WildFly MCP API module to your deployment's dependencies:

```
Dependencies: org.wildfly.extension.mcp.api
```

The `@Tool`, `@ToolArg`, `ToolResponse`, and related annotations are provided by
the `org.mcpjava.server` module, which is transitively available.

## Security Considerations

- **`requestState` integrity**: Configure `request-state-secret` in the MCP subsystem for
  production deployments. Without it, `requestState` tokens are rejected by default.
  See the system property `org.wildfly.extension.mcp.allow-unsigned-request-state` in
  [SECURITY.md](../SECURITY.md) for development overrides.
- **`@McpHeader` values are client-supplied**: Treat them as untrusted input. Do not use
  header values in security decisions without independent validation.
- **Capability checking is advisory**: `ClientCapabilities` reflects what the client
  *declared* during initialization. A malicious client could declare capabilities it does
  not actually support.
