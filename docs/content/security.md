---
title: Security
description: Security architecture, threat model, and deployment hardening for the WildFly AI Feature Pack.
layout: page
---

# Security

## Key Security Controls

- **Origin validation** -- DNS rebinding protection on all MCP HTTP endpoints
- **Session management** -- Cryptographically random session IDs (`SecureRandom` via `UUID.randomUUID()`)
- **Request integrity** -- HMAC-SHA256 signed `requestState` tokens for multi-round tool results
- **Credential protection** -- API keys and passwords managed through WildFly's credential store and `SensitiveTargetAccessConstraintDefinition`
- **Input validation** -- Strict endpoint path validation (`[a-zA-Z0-9\-_]+`), typed JSON deserialization (no polymorphic typing)
- **WASM sandboxing** -- Admin-configured module paths with memory limits and network access control

## Deployment Hardening

### 1. Configure `credential-reference`

When using multi-round tool results (MRTR), configure the MCP subsystem's `credential-reference` attribute with a secret-key credential store entry. Without it, any `requestState` token sent by a client is **rejected** with an `INVALID_PARAMS` JSON-RPC error.

```
/subsystem=elytron/credential-store=mcp-cs:add(relative-to=jboss.server.data.dir, path=mcp-cs.store, create=true, credential-reference={clear-text=changeit})
/subsystem=elytron/credential-store=mcp-cs:generate-secret-key(alias=request-state-secret)
/subsystem=mcp:write-attribute(name=credential-reference, value={store=mcp-cs, alias=request-state-secret})
reload
```

For development only, you can allow unsigned tokens:

```
-Dorg.wildfly.extension.mcp.allow-unsigned-request-state=true
```

This is **not recommended for production** as it allows clients to forge arbitrary request state.

### 2. Restrict Allowed Origins

Configure `allowedOrigins` to expected client origins in production to prevent cross-origin attacks.

### 3. Enable TLS

Enable TLS for all AI provider connections to protect API keys and data in transit.

### 4. Use Credential Stores

Use `credential-reference` with a WildFly credential store instead of plaintext API keys in environment variables.

### 5. WASM Memory Limits

Set memory limits for WASM module configurations to prevent resource exhaustion.

### 6. Disable Debug Logging

Disable debug logging in production to prevent sensitive content (prompts, responses, API keys) from appearing in logs.

## System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `org.wildfly.extension.mcp.allow-unsigned-request-state` | `false` | When `true`, accepts `requestState` tokens without HMAC verification if no secret-key `credential-reference` is configured. **Not recommended for production.** |

## MCP Header Security

`@McpParamHeader` values are **client-supplied** -- treat them as untrusted input. Do not use header values in security decisions without independent validation.

## Client Capabilities

`ClientCapabilities` reflects what the client *declared* during initialization. A malicious client could declare capabilities it does not actually support. Do not rely on capability declarations for security decisions.

## Reporting Vulnerabilities

**Do not open a public issue or pull request for security vulnerabilities.**

Email [security@wildfly.org](mailto:security@wildfly.org) to report security issues. See [SECURITY.md](https://github.com/wildfly/wildfly-ai-feature-pack/blob/main/SECURITY.md) for the full security policy.

## Threat Model

For the complete threat model covering assets, trust boundaries, entry points, and attack surface analysis, see the [threat model document](https://github.com/wildfly/wildfly-ai-feature-pack/blob/main/doc/threat-model.md).
