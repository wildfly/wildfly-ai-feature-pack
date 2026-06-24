package org.wildfly.extension.mcp.api;

import java.util.Arrays;
import java.util.List;
import org.mcp_java.server.content.ContentBlock;
import org.mcp_java.server.content.TextContent;

public record ToolResponse(boolean isError, List<? extends ContentBlock> content) {

    @SafeVarargs
    public static <C extends ContentBlock> ToolResponse success(C... content) {
        return new ToolResponse(false, Arrays.asList(content));
    }

    public static <C extends ContentBlock> ToolResponse success(List<C> content) {
        return new ToolResponse(false, content);
    }

    public static ToolResponse error(String message) {
        return new ToolResponse(true, List.of(TextContent.of(message)));
    }

    public static ToolResponse success(String message) {
        return new ToolResponse(false, List.of(TextContent.of(message)));
    }

}
