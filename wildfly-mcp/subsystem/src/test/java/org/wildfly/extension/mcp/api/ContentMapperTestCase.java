/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.mcp_java.server.Role;
import org.mcp_java.server.content.TextContent;
import org.mcp_java.server.prompts.PromptMessage;
import org.mcp_java.server.prompts.PromptResponse;

public class ContentMapperTestCase {

    @Test
    public void testSinglePromptMessage() {
        PromptMessage input = PromptResponse.of(Role.USER, TextContent.of("Hello")).messages().get(0);

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(1, result.size());
        PromptMessage msg = result.iterator().next();
        assertEquals(Role.USER, msg.role());
        assertTrue("Expected TextContent", msg.content() instanceof TextContent);
    }

    @Test
    public void testAssistantPromptMessage() {
        PromptMessage input = PromptResponse.of(Role.ASSISTANT, TextContent.of("Analysis")).messages().get(0);

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(1, result.size());
        PromptMessage msg = result.iterator().next();
        assertEquals(Role.ASSISTANT, msg.role());
    }

    @Test
    public void testCollectionOfPromptMessages() {
        List<PromptMessage> input = List.of(
                PromptResponse.of(Role.USER, TextContent.of("Question")).messages().get(0),
                PromptResponse.of(Role.ASSISTANT, TextContent.of("Answer")).messages().get(0));

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testPromptMessageArray() {
        PromptMessage[] input = new PromptMessage[] {
                PromptResponse.of(Role.USER, TextContent.of("Q1")).messages().get(0),
                PromptResponse.of(Role.USER, TextContent.of("Q2")).messages().get(0)
        };

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testPlainStringConvertsToUserMessage() {
        String input = "plain text";

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(1, result.size());
        PromptMessage msg = result.iterator().next();
        assertEquals(Role.USER, msg.role());
        TextContent content = (TextContent) msg.content();
        assertEquals("plain text", content.text());
    }

    @Test
    public void testRoleValues() {
        assertEquals("user", Role.USER.name().toLowerCase());
        assertEquals("assistant", Role.ASSISTANT.name().toLowerCase());
    }
}
