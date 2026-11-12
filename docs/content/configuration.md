---
title: Configuration
description: Complete Galleon layer listing and environment variable reference for the WildFly AI Feature Pack.
layout: page
---

# Configuration

## Galleon Layers

The feature pack provides 37 Galleon layers organized by functionality.

### Chat Models

| Layer | Description |
|-------|-------------|
| `ollama-chat-model` | Ollama synchronous chat |
| `openai-chat-model` | OpenAI synchronous chat |
| `mistral-ai-chat-model` | Mistral AI synchronous chat |
| `gemini-chat-model` | Google Gemini synchronous chat |
| `groq-chat-model` | Groq synchronous chat |

### Streaming Chat Models

| Layer | Description |
|-------|-------------|
| `ollama-streaming-chat-model` | Ollama streaming chat |
| `openai-streaming-chat-model` | OpenAI streaming chat |
| `mistral-ai-streaming-chat-model` | Mistral AI streaming chat |
| `gemini-streaming-chat-model` | Google Gemini streaming chat |
| `groq-streaming-chat-model` | Groq streaming chat |

### Embedding Models

| Layer | Description |
|-------|-------------|
| `in-memory-embedding-model-all-minilm-l6-v2` | all-MiniLM-L6-v2 |
| `in-memory-embedding-model-all-minilm-l6-v2-q` | all-MiniLM-L6-v2 (quantized) |
| `in-memory-embedding-model-bge-small-en` | BGE-small-en |
| `in-memory-embedding-model-bge-small-en-q` | BGE-small-en (quantized) |
| `in-memory-embedding-model-bge-small-en-v15` | BGE-small-en-v1.5 |
| `in-memory-embedding-model-bge-small-en-v15-q` | BGE-small-en-v1.5 (quantized) |
| `in-memory-embedding-model-e5-small-v2` | E5-small-v2 |
| `in-memory-embedding-model-e5-small-v2-q` | E5-small-v2 (quantized) |
| `ollama-embedding-model` | Ollama embedding model |

### Embedding Stores

| Layer | Description |
|-------|-------------|
| `in-memory-embedding-store` | In-memory vector store |
| `neo4j-embedding-store` | Neo4j graph-backed vector store |
| `weaviate-embedding-store` | Weaviate vector store |
| `chroma-embedding-store` | Chroma vector store |

### Content Retrievers

| Layer | Description |
|-------|-------------|
| `default-embedding-content-retriever` | Default retriever (in-memory store + all-MiniLM-L6-v2) |
| `neo4j-content-retriever` | Neo4j content retriever |
| `ollama-neo4j-content-retriever` | Neo4j retriever with Ollama embeddings |
| `openai-neo4j-content-retriever` | Neo4j retriever with OpenAI embeddings |

### Other

| Layer | Description |
|-------|-------------|
| `chat-memory-provider` | Chat memory functionality |
| `web-search-engines` | Web search engine integration |
| `mcp-client-sse` | MCP client (SSE transport) |
| `mcp-client-stdio` | MCP client (stdio transport) |
| `mcp-client-streamable` | MCP client (Streamable HTTP transport) |
| `mcp-server` | MCP server support |
| `wasm` | WebAssembly WASI module support |

## Environment Variables

### Ollama

| Variable | Description |
|----------|-------------|
| `OLLAMA_CHAT_URL` | Endpoint URL for the Ollama instance |
| `OLLAMA_CHAT_MODEL_NAME` | Model name (must be downloaded first) |
| `OLLAMA_CHAT_TEMPERATURE` | Temperature for generation |
| `OLLAMA_CHAT_LOG_REQUEST` | Enable request tracing |
| `OLLAMA_CHAT_LOG_RESPONSE` | Enable response tracing |

### OpenAI

| Variable | Description | Required |
|----------|-------------|----------|
| `OPENAI_API_KEY` | API key for authentication | Yes |
| `OPENAI_CHAT_URL` | Endpoint URL | No |
| `OPENAI_CHAT_MODEL_NAME` | Model name | No |
| `OPENAI_CHAT_LOG_REQUEST` | Enable request tracing | No |
| `OPENAI_CHAT_LOG_RESPONSE` | Enable response tracing | No |

### Mistral AI

| Variable | Description | Required |
|----------|-------------|----------|
| `MISTRAL_API_KEY` | API key for authentication | Yes |
| `MISTRAL_CHAT_URL` | Endpoint URL | No |
| `MISTRAL_CHAT_MODEL_NAME` | Model name | No |
| `MISTRAL_CHAT_LOG_REQUEST` | Enable request tracing | No |
| `MISTRAL_CHAT_LOG_RESPONSE` | Enable response tracing | No |

### Google Gemini

| Variable | Description | Required |
|----------|-------------|----------|
| `GEMINI_API_KEY` | API key for authentication | Yes |
| `GEMINI_CHAT_MODEL_NAME` | Model name | No |
| `GEMINI_CHAT_LOG` | Enable request/response tracing | No |

### Groq

| Variable | Description | Required |
|----------|-------------|----------|
| `GROQ_API_KEY` | API key for authentication | Yes |
| `GROQ_CHAT_URL` | Endpoint URL | No |
| `GROQ_CHAT_MODEL_NAME` | Model name | No |
| `GROQ_CHAT_LOG_REQUEST` | Enable request tracing | No |
| `GROQ_CHAT_LOG_RESPONSE` | Enable response tracing | No |

Streaming layer (`groq-streaming-chat-model`) uses the same variables.

### Ollama Embedding Model

| Variable | Description |
|----------|-------------|
| `OLLAMA_EMBEDDING_URL` | Endpoint URL for the Ollama instance |
| `OLLAMA_EMBEDDING_MODEL_NAME` | Embedding model name (must be downloaded first) |
| `OLLAMA_EMBEDDING_LOG_REQUEST` | Enable request tracing |
| `OLLAMA_EMBEDDING_LOG_RESPONSE` | Enable response tracing |

### Neo4j Embedding Store

| Variable | Description |
|----------|-------------|
| `NEO4J_URL` | Bolt URL for the Neo4j server |
| `NEO4J_USER` | Username |
| `NEO4J_PASSWORD` | Password |
| `NEO4J_DIMENSION` | Embedding dimension |

### Neo4j Content Retriever

| Variable | Description |
|----------|-------------|
| `NEO4J_URL` | Bolt URL for the Neo4j server |
| `NEO4J_USER` | Username |
| `NEO4J_PASSWORD` | Password |
| `NEO4J_PROMPT_TEMPLATE` | Prompt template used by the chat model to create Cypher queries |
| `NEO4J_CHAT_LANGUAGE_MODEL` | Chat language model name used to create Cypher queries |

### Weaviate

| Variable | Description |
|----------|-------------|
| `WEAVIATE_HOST` | Host address |
| `WEAVIATE_PORT` | Port number |
| `WEAVIATE_OBJECT_CLASS` | Object class for embeddings |
| `WEAVIATE_SSL_ENABLED` | Enable HTTPS |
| `WEAVIATE_SOCKET_BINDING` | Outbound socket binding name |

### Chroma

| Variable | Description |
|----------|-------------|
| `CHROMA_URL` | Base URL to the Chroma server |
| `CHROMA_API_VERSION` | API version |
| `CHROMA_LOG_REQUEST` | Enable request tracing |
| `CHROMA_LOG_RESPONSE` | Enable response tracing |
| `CHROMA_TIMEOUT` | HTTP call timeout (ms) |

### In-Memory Embedding Store

| Variable | Description | Required |
|----------|-------------|----------|
| `IN_MEMORY_EMBEDDING_FILE` | Filesystem path to load as store content, relative to the server configuration directory | Yes |

### Default Content Retriever

| Variable | Description |
|----------|-------------|
| `EMBEDDING_RETRIEVER_MAX_RESULTS` | Maximum number of contents to retrieve |
| `EMBEDDING_RETRIEVER_MIN_SCORE` | Minimum relevance score (contents below are excluded) |

### Chat Memory

| Variable | Description |
|----------|-------------|
| `CHAT_MEMORY_TYPE` | Type of memory elements: `MESSAGE` or `TOKEN` |
| `CHAT_MEMORY_SIZE` | Number of memory elements stored |
| `CHAT_MEMORY_SESSION` | Use HTTP session ID as memory key |

### Web Search

| Variable | Description | Required |
|----------|-------------|----------|
| `TAVILY_API_KEY` | API key for Tavily search | Yes |

### MCP Client (SSE)

| Variable | Description |
|----------|-------------|
| `MCP_CLIENT_HOST` | MCP server host |
| `MCP_CLIENT_PORT` | MCP server port |
| `MCP_CLIENT_SSE_PATH` | SSE endpoint path |
| `MCP_CLIENT_LOG_REQUEST` | Enable request tracing |
| `MCP_CLIENT_LOG_RESPONSE` | Enable response tracing |
