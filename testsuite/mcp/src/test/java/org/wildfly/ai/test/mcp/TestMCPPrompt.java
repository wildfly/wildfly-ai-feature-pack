/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import org.mcpjava.server.Role;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;

public class TestMCPPrompt {

    @Prompt(name = "greeting", description = "Generates a greeting message")
    PromptResponse greeting(@PromptArg(description = "Name of the person to greet") String name) {
        return PromptResponse.of(Role.USER, TextContent.of("Hello, " + name + "! How can I help you today?"));
    }

    @Prompt(name = "assistant-reply", description = "Generates an assistant reply")
    PromptResponse assistantReply(@PromptArg(description = "Topic to reply about") String topic) {
        return PromptResponse.of(Role.ASSISTANT, TextContent.of("Here is my analysis of " + topic));
    }
}
