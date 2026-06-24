package org.wildfly.extension.mcp.api;

import org.wildfly.mcp.api.prompt.PromptMessage;
import java.util.List;

public record PromptResponse(String description, List<PromptMessage> messages) {

    public static PromptResponse withMessages(List<PromptMessage> messages) {
        return new PromptResponse(null, messages);
    }

}
