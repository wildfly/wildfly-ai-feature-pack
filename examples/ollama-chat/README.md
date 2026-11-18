Ollama Chat Example
===================

A minimal example demonstrating how to use the WildFly AI Feature Pack to chat with an LLM via Ollama.

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
curl "http://localhost:8080/api/chat?message=What+is+WildFly?"
```

Environment Variables
---------------------

| Variable                  | Description          | Default                  |
|---------------------------|----------------------|--------------------------|
| `OLLAMA_CHAT_URL`         | Ollama server URL    | `http://127.0.0.1:11434` |
| `OLLAMA_CHAT_MODEL_NAME`  | Model to use         | `llama3.1:8b`            |
| `OLLAMA_CHAT_TEMPERATURE` | Sampling temperature | `0.9`                    |

How It Works
------------

The `ollama-chat-model` Galleon layer configures an Ollama chat model in the WildFly AI subsystem. The model is exposed as a CDI bean named `"ollama"` and can be injected with `@Inject @Named("ollama") ChatModel`.
