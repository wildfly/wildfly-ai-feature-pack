Ollama Streaming Chat Example
=============================

Demonstrates streaming chat responses from an LLM via Server-Sent Events (SSE).

Tokens are streamed as they are generated, providing a real-time chat experience.

Prerequisites
-------------

- Java 17+
- Maven 3.9+
- [Ollama](https://ollama.com/) running locally with a model pulled (default: `llama3.1:8b`)

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

Try It
------

```bash
curl -N "http://localhost:8080/api/chat?message=Tell+me+a+short+story"
```

The `-N` flag disables buffering so you can see tokens arrive in real time.

Environment Variables
---------------------

| Variable                  | Description          | Default                  |
|---------------------------|----------------------|--------------------------|
| `OLLAMA_CHAT_URL`         | Ollama server URL    | `http://127.0.0.1:11434` |
| `OLLAMA_CHAT_MODEL_NAME`  | Model to use         | `llama3.1:8b`            |
| `OLLAMA_CHAT_TEMPERATURE` | Sampling temperature | `0.9`                    |

How It Works
------------

The `ollama-streaming-chat-model` Galleon layer configures a streaming Ollama chat model. The model is exposed as a CDI bean named `"streaming-ollama"` and can be injected with `@Inject @Named("streaming-ollama") StreamingChatModel`.

The JAX-RS endpoint uses `SseEventSink` and the `StreamingChatResponseHandler` callback interface to push each token to the client as an SSE event.
