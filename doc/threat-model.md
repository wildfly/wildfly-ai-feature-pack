# Threat Model

This document describes the security posture of the WildFly AI Feature Pack. It covers the assets
worth protecting, the trust boundaries, the attack surface, and the vulnerability classes considered
relevant. It does not contain exploit detail or reproduce any specific vulnerability.

## Assets

| Asset | Description |
|-------|-------------|
| AI provider credentials | API keys for OpenAI, Gemini, Mistral, Ollama, and other LLM providers stored in the WildFly management model |
| Database credentials | Neo4j and other embedding-store passwords managed via `credential-reference` |
| Tool invocation integrity | Correctness and authorization of MCP tool/prompt/resource calls |
| MCP session state | Session identifiers and `requestState` tokens used in multi-round tool results |
| Server-side resources | Data exposed through MCP resource endpoints to AI clients |
| WASM module integrity | WebAssembly modules loaded and executed on the server |

## Trust Boundaries

### 1. HTTP/SSE Endpoint (primary attack surface)

Unauthenticated network clients send JSON-RPC messages to the MCP server over HTTP POST
(Streamable HTTP transport) or SSE (Server-Sent Events transport). All input crossing this
boundary is untrusted.

**Controls:**
- Origin / DNS rebinding validation (`MCPServerUtils.validateOrigin`)
- JSON-RPC schema validation (`JsonRPC.validate`)
- Endpoint path restricted to `[a-zA-Z0-9\-_]+` (`EndpointPathValidator`)
- Session IDs generated with `SecureRandom` via `UUID.randomUUID()`
- HMAC-SHA256 integrity on `requestState` tokens (when `request-state-secret` is configured)
- Protocol version negotiation and header consistency checks

### 2. AI Provider APIs (outbound)

The AI subsystem makes outbound HTTPS calls to third-party AI services carrying API keys.
URLs and credentials are admin-configured through the WildFly management model.

**Controls:**
- URLs are management-model attributes, not runtime-controllable
- API keys marked with `SensitiveTargetAccessConstraintDefinition.CREDENTIAL`
- Optional TLS configuration via `ssl-enabled` attribute

### 3. Tool / Prompt / Resource Invocations

User-supplied JSON arguments flow from MCP messages into application logic via
Jackson deserialization and Java reflection. The framework deserializes into
declared concrete types only.

**Controls:**
- Jackson `ObjectMapper` with default configuration (no polymorphic typing)
- Arguments deserialized into tool-declared types, not attacker-controlled types
- Tool lookup via registry keyed by name; no arbitrary class instantiation
- CDI-managed bean resolution with fallback to reflection on declared classes

### 4. WildFly Management Model (admin)

Server administrators configure AI providers, MCP endpoints, WASM modules, and
credentials through the WildFly management interface. Values set here are trusted.

**Controls:**
- Standard WildFly RBAC for management operations
- `CredentialReference` / `CredentialSource` for passwords
- Path validation on WASM module paths via `PathManager`

### 5. WASM Execution

WebAssembly modules are loaded from admin-configured paths and executed in a
sandboxed runtime (Extism/Chicory).

**Controls:**
- Module paths are admin-configured, not client-controllable
- Sandboxed execution environment
- Configurable memory limits (`min-memory-constraint`, `max-memory-constraint`)
- Network access restricted via `allowedHosts` (defaults to none)

## Entry Points

| Entry Point | Transport | Handler |
|-------------|-----------|---------|
| `POST /mcp` | Streamable HTTP | `StreamableHttpHandler` |
| `GET /mcp` | Streamable HTTP (SSE stream) | `StreamableHttpHandler` |
| `POST /mcp/sse/<id>` | SSE messages | `MessagesHttpHandler` |
| SSE connection | SSE handshake | `MCPServerSentConnectionCallBack`, `MCPStreamableConnectionCallBack` |

## Attacker Model

| Attacker | Access | Objectives |
|----------|--------|------------|
| Unauthenticated remote client | Network access to MCP HTTP endpoint | Invoke tools without authorization, forge session state, exfiltrate data |
| Authenticated MCP client | Valid MCP session | Escalate privileges via `requestState` forgery, access other sessions |
| Malicious AI client | MCP protocol access | Exploit tool invocations, inject via prompt/resource arguments |
| Local admin | WildFly management console | Misconfigure security settings (out of scope for this project) |

## Vulnerability Classes Considered

The following classes were assessed during security review, scoped to what this project
credibly defends against:

- **Injection** (JSON-RPC, command, expression) -- mitigated by typed deserialization
- **Deserialization of untrusted data** -- mitigated by default Jackson config (no polymorphic typing)
- **SSRF** -- mitigated by admin-only URL configuration
- **Path traversal** -- mitigated by `EndpointPathValidator` and `PathManager`
- **XXE** -- not applicable (no XML parsing; all JSON via `jakarta.json`)
- **DNS rebinding** -- mitigated by `MCPServerUtils.validateOrigin`
- **Session fixation / prediction** -- mitigated by `SecureRandom`-based UUIDs
- **Cryptographic integrity** -- HMAC-SHA256 with constant-time comparison for `requestState`;
  unsigned tokens are rejected by default (see Deployment Recommendations)
- **Credential exposure** -- mitigated by WildFly `SensitiveTargetAccessConstraintDefinition`
- **DoS** -- delegated to WildFly/Undertow connection and thread pool limits

## Out of Scope

The following are explicitly outside this project's security perimeter:

- WildFly core security subsystem and Elytron
- Undertow HTTP server and TLS implementation
- JDK cryptographic providers
- Third-party AI provider API security
- Network-level protections (firewalls, rate limiting, WAF)
- Security of user-written MCP tool implementations
- Operating system and container security

## Deployment Recommendations

1. **Configure `request-state-secret`** when using multi-round tool results (MRTR) to enable
   HMAC integrity verification on `requestState` tokens. Without this attribute, any
   `requestState` sent by a client is rejected by default. The system property
   `org.wildfly.extension.mcp.allow-unsigned-request-state` can be set to `true` to accept
   unsigned tokens during development, but this is **not recommended for production**.
2. **Restrict `allowedOrigins`** in production to the expected client origins.
3. **Enable TLS** (`ssl-enabled`) for all AI provider connections.
4. **Use `credential-reference`** with a credential store rather than plaintext `api-key`
   attributes where possible.
5. **Set WASM memory limits** (`min-memory-constraint`, `max-memory-constraint`) for all
   WASM module configurations.
6. **Disable debug logging** in production to prevent sensitive MCP message content from
   appearing in logs.
