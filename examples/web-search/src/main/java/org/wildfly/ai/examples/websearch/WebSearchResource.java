/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.websearch;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Path("/search")
public class WebSearchResource {

    @Inject
    @Named("web-search-retriever")
    private ContentRetriever webSearchRetriever;

    @Inject
    @Named("ollama")
    private ChatModel chatModel;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String search(@QueryParam("query") String query) {
        List<Content> searchResults = webSearchRetriever.retrieve(Query.from(query));

        String context = searchResults.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        String augmentedPrompt = "Based on the following web search results, answer the question.\n\n"
                + "Search results:\n" + context + "\n\n"
                + "Question: " + query;

        return chatModel.chat(augmentedPrompt);
    }
}
