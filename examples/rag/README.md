RAG (Retrieval-Augmented Generation) Example
=============================================

Demonstrates Retrieval-Augmented Generation using the WildFly AI Feature Pack. Documents are embedded and stored in-memory, then retrieved as context when querying the LLM.

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

First, ingest some documents:

```bash
curl -X POST http://localhost:8080/api/rag/ingest -H "Content-Type: text/plain" -d "WildFly is a flexible, lightweight Java application server. It implements the latest Jakarta EE standards."

curl -X POST http://localhost:8080/api/rag/ingest -H "Content-Type: text/plain" -d "The WildFly AI Feature Pack provides integration with LangChain4j for AI capabilities including chat models, embeddings, and RAG."
```

Then query with augmented context:

```bash
curl "http://localhost:8080/api/rag/query?question=What+AI+capabilities+does+WildFly+support?"
```

The response will be grounded in the ingested documents rather than relying solely on the LLM's training data.

Environment Variables
---------------------

| Variable                          | Description                            | Default                  |
|-----------------------------------|----------------------------------------|--------------------------|
| `OLLAMA_CHAT_URL`                 | Ollama server URL                      | `http://127.0.0.1:11434` |
| `OLLAMA_CHAT_MODEL_NAME`          | Model to use                           | `llama3.1:8b`            |
| `EMBEDDING_RETRIEVER_MAX_RESULTS` | Maximum number of retrieved segments   | `2`                      |
| `EMBEDDING_RETRIEVER_MIN_SCORE`   | Minimum similarity score for retrieval | `0.7`                    |

How It Works
------------

The `default-embedding-content-retriever` Galleon layer provisions:
- An in-memory embedding store (`@Named("in-memory")`)
- The `all-minilm-l6-v2` embedding model (`@Named("all-minilm-l6-v2")`)
- A content retriever (`@Named("embedding-store-retriever")`) that combines both

The `/ingest` endpoint embeds text and stores it. The `/query` endpoint retrieves semantically relevant content and augments the LLM prompt with it.
