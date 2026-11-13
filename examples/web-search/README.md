Web Search Example
==================

Demonstrates web search integration using the WildFly AI Feature Pack. Web search results from Tavily are used to augment LLM responses with up-to-date information from the internet.

Prerequisites
-------------

- Java 17+
- Maven 3.9+
- [Ollama](https://ollama.com/) running locally with a model pulled (default: `llama3.1:8b`)
- A [Tavily](https://tavily.com/) API key

Build
-----

```bash
mvn clean package
```

Run
---

**Important:** The `TAVILY_API_KEY` environment variable **must** be set before starting the server.

```bash
export TAVILY_API_KEY=your-tavily-api-key
./target/server/bin/standalone.sh --stability experimental
```

You can obtain an API key by signing up at [tavily.com](https://tavily.com/).

Try It
------

```bash
curl "http://localhost:8080/api/search?query=What+is+the+latest+version+of+WildFly?"
```

The response will be augmented with real-time web search results.

Environment Variables
---------------------

| Variable                 | Description                  | Default                  |
|--------------------------|------------------------------|--------------------------|
| `TAVILY_API_KEY`         | **Required.** Tavily API key | (none)                   |
| `OLLAMA_CHAT_URL`        | Ollama server URL            | `http://127.0.0.1:11434` |
| `OLLAMA_CHAT_MODEL_NAME` | Model to use                 | `llama3.1:8b`            |

How It Works
------------

The `web-search-engines` Galleon layer provisions the web search content retriever with Tavily as the default provider. The API key is read from the `TAVILY_API_KEY` environment variable at runtime.

The retriever is exposed as a CDI bean named `"web-search-retriever"`. The endpoint retrieves web search results for the user's query, then passes them as context to the chat model for a grounded response.