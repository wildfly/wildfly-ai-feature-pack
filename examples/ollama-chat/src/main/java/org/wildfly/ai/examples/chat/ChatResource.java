/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.chat;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/chat")
public class ChatResource {

    @Inject
    @Named("ollama")
    private ChatModel chatModel;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@QueryParam("message") String message) {
        return chatModel.chat(message);
    }
}
