WildFly AI Feature Pack Examples
=================================

This directory contains standalone example applications demonstrating the features provided by the WildFly AI Feature Pack.

Each example is an independent Maven WAR project that can be built and deployed separately.

Prerequisites
-------------

- Java 17+
- Maven 3.9+
- The WildFly AI Feature Pack artifacts installed locally (run `mvn install` from the project root if using SNAPSHOT versions)

Examples
--------

| Example                                         | Description                                                   | Key Layers                                                 |
|-------------------------------------------------|---------------------------------------------------------------|------------------------------------------------------------|
| [ollama-chat](ollama-chat/)                     | Basic chat with an LLM using Ollama                           | `ollama-chat-model`                                        |
| [ollama-streaming-chat](ollama-streaming-chat/) | Streaming chat responses via Server-Sent Events               | `ollama-streaming-chat-model`                              |
| [chat-memory](chat-memory/)                     | Conversational memory that maintains chat context             | `ollama-chat-model`, `chat-memory-provider`                |
| [rag](rag/)                                     | Retrieval-Augmented Generation with in-memory embedding store | `ollama-chat-model`, `default-embedding-content-retriever` |
| [mcp-server](mcp-server/)                       | MCP server exposing tools, resources, and prompts             | `mcp-server`                                               |
| [web-search](web-search/)                       | Web search integration as a content retriever                 | `ollama-chat-model`, `web-search-engines`                  |
| [wasm](wasm/)                                   | WebAssembly WASI module invocation (proof-of-concept)         | `wasm`                                                     |

Building an Example
-------------------

Each example follows the same build pattern:

```bash
cd examples/<example-name>
mvn clean package
```

This provisions a WildFly server with the required Galleon layers in `target/server/`.

Running an Example
------------------

```bash
./target/server/bin/standalone.sh --stability experimental
```

The application deploys as `ROOT.war` and is accessible at `http://localhost:8080/`.

Stability Level
---------------

The WildFly AI Feature Pack operates at the **experimental** stability level. You must specify `--stability experimental` when starting the server.
