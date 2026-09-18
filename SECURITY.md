# Security Policy

## Supported Versions

Security fixes are applied to the most recent minor release of the WildFly AI Feature Pack only.

| Version | Supported          |
|---------|--------------------|
| Latest  | :white_check_mark: |
| Older   | :x:                |

## Reporting of CVEs and Security Issues

### The WildFly community and our sponsor, Red Hat, take security bugs very seriously

We aim to take immediate action to address serious security-related problems that involve our projects.

### Reporting a Vulnerability

When reporting a security vulnerability it is important to not accidentally broadcast to the world that the issue exists, as this makes it easier for people to exploit it. The software industry uses the term [embargo](https://www.redhat.com/en/blog/security-embargoes-red-hat) to describe the time a security issue is known internally until it is public knowledge.

**Do not open a public issue, send a pull request, or disclose any information about the suspected vulnerability publicly, including in your own publicly visible git repository.**

#### Email the mailing list

The list at [security@wildfly.org](mailto:security@wildfly.org) is the preferred mechanism for outside users to report security issues. A member of the WildFly team will open the required issues.

#### Collaborate on a fix

If you would like to work with us on a fix for the security vulnerability, please include your GitHub username in the above email, and we will provide you access to a temporary private fork where we can collaborate on a fix without it being disclosed publicly.

If you discover any publicly disclosed security vulnerabilities, please notify us immediately through [security@wildfly.org](mailto:security@wildfly.org).

## Security Architecture

This project provides WildFly subsystem extensions for AI integration (LLM providers, MCP server,
WASM execution). See [doc/threat-model.md](doc/threat-model.md) for the full threat model covering
assets, trust boundaries, entry points, and deployment recommendations.

### Key Security Controls

- **Origin validation**: DNS rebinding protection on all MCP HTTP endpoints
- **Session management**: Cryptographically random session IDs (`SecureRandom`)
- **Request integrity**: HMAC-SHA256 signed `requestState` tokens for multi-round tool results.
  Unsigned `requestState` tokens are **rejected by default**; see [Deployment Hardening](#deployment-hardening).
- **Credential protection**: API keys and passwords managed through WildFly's credential store
  and `SensitiveTargetAccessConstraintDefinition`
- **Input validation**: Strict endpoint path validation, typed JSON deserialization (no polymorphic typing)
- **WASM sandboxing**: Admin-configured module paths with memory limits and network access control

### Deployment Hardening

1. **Configure `request-state-secret`** when using multi-round tool results (MRTR). Without this
   attribute, any `requestState` token sent by a client is rejected with an `INVALID_PARAMS`
   JSON-RPC error. If you need to accept unsigned tokens during development, set the system
   property `org.wildfly.extension.mcp.allow-unsigned-request-state=true` — this is **not
   recommended for production** as it allows clients to forge arbitrary request state.
2. Restrict `allowedOrigins` to expected client origins in production
3. Enable TLS for all AI provider connections
4. Use `credential-reference` with a credential store instead of plaintext API keys
5. Set memory limits for WASM module configurations
6. Disable debug logging in production to prevent sensitive content in logs

### System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `org.wildfly.extension.mcp.allow-unsigned-request-state` | `false` | When `true`, accepts `requestState` tokens without HMAC verification if no `request-state-secret` is configured. **Not recommended for production.** |
