/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import java.util.List;
import org.mcp_java.server.prompts.Prompt;
import org.mcp_java.server.prompts.PromptArg;
import org.wildfly.mcp.model.content.TextContent;
import org.wildfly.mcp.model.prompt.PromptMessage;

public class TestMCPPrompt {

    @Prompt(name = "greeting", description = "Generates a greeting message")
    PromptMessage greeting(@PromptArg(description = "Name of the person to greet") String name) {
        return PromptMessage.user(List.of(TextContent.of("Hello, " + name + "! How can I help you today?")));
    }

    @Prompt(name = "assistant-reply", description = "Generates an assistant reply")
    PromptMessage assistantReply(@PromptArg(description = "Topic to reply about") String topic) {
        return PromptMessage.assistant(List.of(TextContent.of("Here is my analysis of " + topic)));
    }
}
