---
title: Tool Development
description: Advanced MCP tool APIs -- transport headers, client capabilities, and multi-round tool interactions.
layout: page
---

# MCP Tool Development

This guide covers the advanced APIs for building MCP tools that go beyond simple request/response: inspecting client capabilities, passing out-of-band context via HTTP headers, and implementing multi-round tool interactions.

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

Inject `ClientCapabilities` as a tool method parameter -- the framework resolves it automatically (it does not appear in the tool's input schema).

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

Use `requireCapability()` when a tool cannot function without a specific feature:

```java
@Tool(name = "requires_elicitation", description = "Tool that must elicit input")
Object elicitingTool(ClientCapabilities capabilities, InputResponses input) {
    capabilities.requireCapability("elicitation");
    // safe to send elicitation requests from here on
}
```

## McpParamHeader

`org.wildfly.mcp.api.McpParamHeader` designates a `@Tool` method parameter whose value clients must mirror into an HTTP request header. It is a **supplemental annotation to `@ToolArg`** -- `@ToolArg` describes the property (name, description, required status) while `@McpParamHeader` adds the `x-mcp-header` keyword to that same property in the tool's JSON Schema.

When a client calls the tool over Streamable HTTP transport, it sends the argument value in the `Mcp-Param-<name>` header alongside the request body, enabling intermediaries (gateways, load balancers) to route or inspect calls without parsing the body.

### Annotation Attribute

| Attribute | Type | Description |
|-----------|------|-------------|
| `value` | `String` | The header name carried by the `x-mcp-header` keyword |

The `required` status and `description` are specified on the companion `@ToolArg` annotation.

### Constraints

- `value()` must not be empty.
- `value()` must contain only ASCII characters, excluding space and `:`.
- `value()` must be unique (case-insensitive) among the parameters of a single tool.
- The annotated parameter must map to a primitive JSON Schema type: `integer`, `string`, or `boolean` -- `number` is not permitted.

### Usage

```java
import org.wildfly.mcp.api.McpParamHeader;

@Tool(name = "greet", description = "Greets in the requested language")
String greet(@ToolArg(required = false, description = "Language for greeting")
             @McpParamHeader("language") String language,
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
String secureOp(@ToolArg(description = "Auth token")
                @McpParamHeader("auth-token") String token) {
    // token is guaranteed non-null here (required defaults to true on @ToolArg)
    return "Authenticated: " + token;
}
```

Multiple headers can be combined with regular tool arguments:

```java
@Tool(name = "multi_header", description = "Uses multiple headers")
String multiHeader(@ToolArg(description = "Auth token") @McpParamHeader("token") String token,
                   @ToolArg(description = "Input data") String data,
                   @ToolArg(description = "Tenant identifier") @McpParamHeader("tenant-id") String tenantId) {
    return "tenant=" + tenantId + " data=" + data;
}
```

### Generated JSON Schema

For the `greet` example above, the generated input schema is:

```json
{
  "type": "object",
  "properties": {
    "language": {
      "type": "string",
      "description": "Language for greeting",
      "x-mcp-header": "language"
    },
    "name": {
      "type": "string",
      "description": "Name to greet"
    }
  },
  "required": ["name"]
}
```

### Header Resolution

Values are resolved from two sources, in order of precedence:

1. HTTP request header: `Mcp-Param-<name>` (e.g. `Mcp-Param-language`)
2. JSON-RPC payload: `params._meta.headers.<name>`

The HTTP header takes precedence when both are present. When both the header and the body argument are present, the server validates that they match -- a mismatch returns a `400` error.

### Sending Headers from a Client

Over HTTP, set the `Mcp-Param-` prefixed header on the request:

```
POST /mcp HTTP/1.1
Mcp-Param-language: fr
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

The MRTR flow lets a tool pause execution to request additional input from the client -- user elicitation, LLM sampling, or a roots listing -- then resume with the responses.

### How It Works

```
Client                         Server (Tool)
  |                                |
  |  tools/call (name, args)       |
  | ------------------------------>|
  |                                |  (first call: no responses)
  |  resultType: input_required    |
  |  inputRequests: (...)          |
  |  requestState: "opaque"        |
  | <------------------------------|
  |                                |
  |  tools/call (name, args,       |
  |    inputResponses, requestState)|
  | ------------------------------>|
  |                                |  (follow-up: has responses)
  |  resultType: ...               |
  |  content: [final result]       |
  | <------------------------------|
```

### InputResponses

`org.wildfly.mcp.api.tool.InputResponses` is injected as a tool parameter:

| Method | Description |
|--------|-------------|
| `boolean hasResponses()` | `true` if the client sent `inputResponses` in this call |
| `Map<String, JsonObject> responses()` | The responses keyed by the request key |
| `String requestState()` | The `requestState` string echoed back by the client |

### InputRequiredResult

`org.wildfly.mcp.api.tool.InputRequiredResult` is returned by the tool to signal that more input is needed:

| Builder Method | Description |
|----------------|-------------|
| `addElicitation(key, message, schema)` | Request user input via `elicitation/create` |
| `addSampling(key, messages, maxTokens)` | Request LLM completion via `sampling/createMessage` |
| `addRootsList(key)` | Request the client's root URIs via `roots/list` |
| `addInputRequest(key, method, params)` | Add a raw input request with any method |
| `requestState(state)` | Set opaque server state to be echoed back |

### Example -- Confirmation Dialog

```java
import org.wildfly.mcp.api.tool.InputRequiredResult;
import org.wildfly.mcp.api.tool.InputResponses;

@Tool(name = "confirm_delete", description = "Asks for confirmation before deleting")
Object confirmDelete(@ToolArg(description = "Item to delete") String item,
                     InputResponses input) {
    if (!input.hasResponses()) {
        return InputRequiredResult.builder()
                .addElicitation("confirm",
                    "Are you sure you want to delete '" + item + "'?",
                    Json.createObjectBuilder()
                        .add("type", "object")
                        .add("properties", Json.createObjectBuilder()
                            .add("ok", Json.createObjectBuilder()
                                .add("type", "boolean")))
                        .add("required", Json.createArrayBuilder().add("ok")))
                .build();
    }
    JsonObject confirmResponse = input.responses().get("confirm");
    boolean confirmed = confirmResponse.getJsonObject("content")
            .getBoolean("ok", false);
    if (confirmed) {
        return ToolResponse.ofText("Deleted: " + item);
    }
    return ToolResponse.ofText("Deletion cancelled");
}
```

### requestState Security

When the MCP subsystem's `credential-reference` (a secret-key credential store entry) is configured, the server HMAC-signs the `requestState` to prevent tampering. **Without it, unsigned `requestState` tokens are rejected by default.** See [Security](security) for deployment guidance.

## Combining All APIs

A tool can use all APIs together:

```java
@Tool(name = "full_featured", description = "Uses headers, capabilities, and MRTR")
Object fullFeatured(@ToolArg(description = "Tenant identifier") @McpParamHeader("tenant") String tenant,
                    @ToolArg(description = "Action to perform") String action,
                    ClientCapabilities capabilities,
                    InputResponses input) {
    if (input.hasResponses()) {
        return ToolResponse.ofText("Completed for tenant " + tenant);
    }
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
    return ToolResponse.ofText("Executed " + action + " for tenant " + tenant);
}
```

## Dependency

Add the WildFly MCP API module to your deployment's dependencies:

```
Dependencies: org.wildfly.extension.mcp.api
```

The `@Tool`, `@ToolArg`, `ToolResponse`, and related annotations are provided by the `org.mcpjava.server` module, which is transitively available.
