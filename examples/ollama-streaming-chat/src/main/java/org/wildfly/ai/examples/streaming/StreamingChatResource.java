/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.streaming;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

@Path("/chat")
public class StreamingChatResource {

    private static final Logger LOG = Logger.getLogger(StreamingChatResource.class.getName());

    @Inject
    @Named("streaming-ollama")
    private StreamingChatModel streamingChatModel;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void chat(@QueryParam("message") String message,
                     @Context SseEventSink eventSink,
                     @Context Sse sse) {
        streamingChatModel.chat(message, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (!eventSink.isClosed()) {
                    eventSink.send(sse.newEvent(partialResponse));
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                closeSink(eventSink);
            }

            @Override
            public void onError(Throwable error) {
                if (!eventSink.isClosed()) {
                    eventSink.send(sse.newEvent("error", error.getMessage()));
                }
                closeSink(eventSink);
            }
        });
    }

    private static void closeSink(SseEventSink eventSink) {
        if (!eventSink.isClosed()) {
            try {
                eventSink.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to close SSE event sink", e);
            }
        }
    }
}
