/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.wildfly.mcp.model.Role;
import org.wildfly.mcp.model.content.TextContent;
import org.wildfly.mcp.model.prompt.PromptMessage;

public class ContentMapperTestCase {

    @Test
    public void testSinglePromptMessage() {
        PromptMessage input = PromptMessage.user(List.of(TextContent.of("Hello")));

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(1, result.size());
        PromptMessage msg = result.iterator().next();
        assertEquals(Role.USER, msg.role());
        assertEquals(1, msg.content().size());
        assertEquals("text", msg.content().get(0).type());
    }

    @Test
    public void testAssistantPromptMessage() {
        PromptMessage input = PromptMessage.assistant(List.of(TextContent.of("Analysis")));

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(1, result.size());
        PromptMessage msg = result.iterator().next();
        assertEquals(Role.ASSISTANT, msg.role());
    }

    @Test
    public void testCollectionOfPromptMessages() {
        List<PromptMessage> input = List.of(
                PromptMessage.user(List.of(TextContent.of("Question"))),
                PromptMessage.assistant(List.of(TextContent.of("Answer"))));

        Collection<? extends PromptMessage> result = ContentMapper.processResultAsPromptMessage(input);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testPromptMessageArray() {
        PromptMessage[] input = new PromptMessage[] {
                PromptMessage.user(List.of(TextContent.of("Q1"))),
                PromptMessage.user(List.of(TextContent.of("Q2")))
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
        assertEquals(1, msg.content().size());
        TextContent content = (TextContent) msg.content().get(0);
        assertEquals("plain text", content.text());
    }

    @Test
    public void testRoleValues() {
        assertEquals("user", Role.USER.getValue());
        assertEquals("assistant", Role.ASSISTANT.getValue());
    }
}
