/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Path("/rag")
public class RagResource {

    @Inject
    @Named("all-minilm-l6-v2")
    private EmbeddingModel embeddingModel;

    @Inject
    @Named("in-memory")
    private EmbeddingStore embeddingStore;

    @Inject
    @Named("embedding-store-retriever")
    private ContentRetriever contentRetriever;

    @Inject
    @Named("ollama")
    private ChatModel chatModel;

    @POST
    @Path("/ingest")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String ingest(String text) {
        TextSegment segment = TextSegment.from(text);
        embeddingStore.add(embeddingModel.embed(text).content(), segment);
        return "Ingested: " + text.substring(0, Math.min(text.length(), 80)) + "...";
    }

    @GET
    @Path("/query")
    @Produces(MediaType.TEXT_PLAIN)
    public String query(@QueryParam("question") String question) {
        List<Content> relevantContent = contentRetriever.retrieve(Query.from(question));

        String context = relevantContent.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        String augmentedPrompt = "Based on the following context, answer the question.\n\n"
                + "Context:\n" + context + "\n\n"
                + "Question: " + question;

        return chatModel.chat(augmentedPrompt);
    }
}
