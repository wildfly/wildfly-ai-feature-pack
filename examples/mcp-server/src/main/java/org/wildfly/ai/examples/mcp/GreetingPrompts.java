/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.examples.mcp;

import java.util.List;
import org.mcp_java.model.content.TextContent;
import org.mcp_java.model.prompt.PromptMessage;
import org.mcp_java.server.prompts.Prompt;
import org.mcp_java.server.prompts.PromptArg;

public class GreetingPrompts {

    @Prompt(name = "greeting", description = "Generates a personalized greeting message")
    public PromptMessage greeting(
            @PromptArg(description = "Name of the person to greet") String name) {
        return PromptMessage.user(List.of(
                TextContent.of("Please greet " + name + " warmly and ask how you can help them today.")));
    }

    @Prompt(name = "summarize", description = "Generates a prompt to summarize text")
    public PromptMessage summarize(
            @PromptArg(description = "Text to summarize") String text) {
        return PromptMessage.user(List.of(
                TextContent.of("Please provide a concise summary of the following text:\n\n" + text)));
    }
}
