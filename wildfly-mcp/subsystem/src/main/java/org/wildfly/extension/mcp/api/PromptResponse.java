package org.wildfly.extension.mcp.api;

import org.mcpjava.server.prompts.PromptMessage;
import java.util.List;

public record PromptResponse(String description, List<PromptMessage> messages) {

    public static PromptResponse withMessages(List<PromptMessage> messages) {
        return new PromptResponse(null, messages);
    }

}
