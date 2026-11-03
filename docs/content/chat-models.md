---
title: Chat Models
description: Configure LLM chat model providers -- OpenAI, Ollama, Mistral, Gemini, and Groq.
layout: page
---

# Chat Models

The feature pack supports connecting to multiple LLM providers for both synchronous and streaming chat interactions. Each provider is exposed as a Galleon layer that you include when provisioning your WildFly server.

## Supported Providers

### Synchronous Chat Models

| Layer | Provider | API Key Required |
|-------|----------|-----------------|
| `ollama-chat-model` | [Ollama](https://ollama.com/) | No |
| `openai-chat-model` | [OpenAI](https://platform.openai.com/) | Yes |
| `mistral-ai-chat-model` | [Mistral AI](https://mistral.ai/) | Yes |
| `gemini-chat-model` | [Google Gemini](https://ai.google.dev/) | Yes |
| `groq-chat-model` | [Groq](https://groq.com/) | Yes |

### Streaming Chat Models

| Layer | Provider | API Key Required |
|-------|----------|-----------------|
| `ollama-streaming-chat-model` | Ollama | No |
| `openai-streaming-chat-model` | OpenAI | Yes |
| `mistral-ai-streaming-chat-model` | Mistral AI | Yes |
| `gemini-streaming-chat-model` | Google Gemini | Yes |
| `groq-streaming-chat-model` | Groq | Yes |

## Provider Configuration

Each chat model layer is configured via environment variables. All layers also support the `executor-service` attribute for async execution via a `ManagedExecutorService`.

### Ollama

```bash
export OLLAMA_CHAT_URL=http://localhost:11434
export OLLAMA_CHAT_MODEL_NAME=llama3
export OLLAMA_CHAT_TEMPERATURE=0.7
export OLLAMA_CHAT_LOG_REQUEST=true
export OLLAMA_CHAT_LOG_RESPONSE=true
```

### OpenAI

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_CHAT_URL=https://api.openai.com/v1
export OPENAI_CHAT_MODEL_NAME=gpt-4o
export OPENAI_CHAT_LOG_REQUEST=true
export OPENAI_CHAT_LOG_RESPONSE=true
```

### Mistral AI

```bash
export MISTRAL_API_KEY=...
export MISTRAL_CHAT_URL=https://api.mistral.ai/v1
export MISTRAL_CHAT_MODEL_NAME=mistral-large-latest
export MISTRAL_CHAT_LOG_REQUEST=true
export MISTRAL_CHAT_LOG_RESPONSE=true
```

### Google Gemini

```bash
export GEMINI_API_KEY=...
export GEMINI_CHAT_MODEL_NAME=gemini-pro
export GEMINI_CHAT_LOG=true
```

### Groq

```bash
export GROQ_API_KEY=gsk_...
export GROQ_CHAT_URL=https://api.groq.com/openai/v1
export GROQ_CHAT_MODEL_NAME=llama-3.3-70b-versatile
export GROQ_CHAT_LOG_REQUEST=true
export GROQ_CHAT_LOG_RESPONSE=true
```

## Chat Memory

The `chat-memory-provider` layer provides conversation memory for chat models.

```bash
export CHAT_MEMORY_TYPE=MESSAGE    # MESSAGE or TOKEN
export CHAT_MEMORY_SIZE=10         # Number of memory elements stored
export CHAT_MEMORY_SESSION=true    # Use HTTP session ID as memory key
```

## Web Search

The `web-search-engines` layer integrates web search (via Tavily) into your AI pipeline:

```bash
export TAVILY_API_KEY=tvly-...
```

## Next Steps

- [Embedding & RAG](embedding) -- Set up embedding stores and content retrievers
- [Configuration](configuration) -- Full environment variable reference
