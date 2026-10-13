Chat Memory Example
===================

Demonstrates conversational memory using the WildFly AI Feature Pack. The LLM remembers previous messages in the conversation, enabling multi-turn interactions.

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

### Memory Types

The subsystem supports two memory strategies controlled by the `CHAT_MEMORY_TYPE` environment variable:

- **`MESSAGE`** (default) — Retains the last N messages (user + assistant). Controlled by `CHAT_MEMORY_SIZE` (default: `5`).

- **`TOKEN`** — Retains as many recent messages as fit within a token budget. Controlled by `CHAT_MEMORY_SIZE` which sets the maximum number of tokens.

```bash
./target/server/bin/standalone.sh --stability experimental
```

Try It
------

Send multiple messages and notice the model remembers the conversation. Use a cookie jar (`-b`/`-c`) so `curl` maintains the HTTP session between requests:

```bash
curl -X POST http://localhost:8080/api/chat -H "Content-Type: text/plain" -d "My name is Alice" -c cookies.txt -b cookies.txt
curl -X POST http://localhost:8080/api/chat -H "Content-Type: text/plain" -d "What is my name?" -c cookies.txt -b cookies.txt
```

The second response should reference "Alice" since the conversation memory is maintained across requests within the same HTTP session.

To try token-based memory, stop the server and restart with:

```bash
export CHAT_MEMORY_TYPE=TOKEN
export CHAT_MEMORY_SIZE=200
./target/server/bin/standalone.sh --stability experimental
```

Then send a few messages to see the token window in action:

```bash
rm -f cookies.txt
curl -X POST http://localhost:8080/api/chat -H "Content-Type: text/plain" -d "My name is Bob and I live in Amsterdam" -c cookies.txt -b cookies.txt
curl -X POST http://localhost:8080/api/chat -H "Content-Type: text/plain" -d "I work as a software engineer at a startup" -c cookies.txt -b cookies.txt
curl -X POST http://localhost:8080/api/chat -H "Content-Type: text/plain" -d "What do you remember about me?" -c cookies.txt -b cookies.txt
```

With a small token budget (200), earlier messages may be evicted as the conversation grows. The third response might remember your job but not your name, showing how token-based memory prioritizes recent context.

Environment Variables
---------------------

| Variable                 | Description                           | Default                  |
|--------------------------|---------------------------------------|--------------------------|
| `OLLAMA_CHAT_URL`        | Ollama server URL                     | `http://127.0.0.1:11434` |
| `OLLAMA_CHAT_MODEL_NAME` | Model to use                          | `llama3.1:8b`            |
| `CHAT_MEMORY_SESSION`    | Use HTTP session for memory isolation | `true`                   |
| `CHAT_MEMORY_TYPE`       | Memory type (`MESSAGE` or `TOKEN`)    | `MESSAGE`                |
| `CHAT_MEMORY_SIZE`       | Number of messages/tokens to keep     | `5`                      |

How It Works
------------

The `chat-memory-provider` Galleon layer configures a chat memory provider in the WildFly AI subsystem. Combined with `@RegisterAIService`, LangChain4j CDI automatically creates an AI service implementation that maintains conversation history.

The `AssistantService` interface uses `@RegisterAIService(chatModelName = "ollama", chatMemoryProviderName = "chat-memory")` to wire the chat model and memory provider together. LangChain4j CDI generates the implementation at deployment time.
