MCP Server Example
==================

Demonstrates how to expose a Jakarta EE application as a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server using the WildFly AI Feature Pack.

This example exposes:
- **Tools**: Calculator operations (add, multiply, subtract)
- **Resources**: Server info and environment variable lookup
- **Prompts**: Greeting and summarization prompt templates

Prerequisites
-------------

- Java 17+
- Maven 3.9+

No LLM is required for this example -- the MCP server exposes capabilities that MCP clients can invoke.

Build
-----

```bash
mvn clean package
```

Run
---

```bash
./target/server/bin/standalone.sh --stability experimental
```

The MCP server is available at `http://localhost:8080/`.

Try it
------------------------

You can connect using any MCP-compatible client such as [Claude Desktop](https://claude.ai/download) or [Claude Code](https://docs.anthropic.com/en/docs/claude-code).

Add this server as an StreamableEvent MCP server with the URL `http://localhost:8080/stream`.

MCP Inspector example:
```bash
npx @modelcontextprotocol/inspector stream http://localhost:8080/stream
```

How It Works
------------

The `mcp-server` Galleon layer enables MCP server support in WildFly. Classes containing methods annotated with `@Tool`, `@Resource`, `@ResourceTemplate`, or `@Prompt` (from `org.mcp-java:mcp-server-api`) are automatically discovered and registered.

- `CalculatorTools.java` -- `@Tool` annotations expose methods as callable MCP tools
- `InfoResources.java` -- `@Resource` and `@ResourceTemplate` annotations expose data sources
- `GreetingPrompts.java` -- `@Prompt` annotations expose reusable prompt templates
