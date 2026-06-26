/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.test.mcp;

import java.util.List;

import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.wildfly.extension.mcp.injection.tool.ToolSchemaGenerator;
import org.wildfly.mcp.model.tool.InputSchema;
import org.wildfly.mcp.model.tool.OutputSchema;

public class TestMCPSchemaGeneratorTool {

    public record SearchResult(int count, List<String> items) {
    }

    public record AddResult(int sum, String expression) {
    }

    public static class SearchInputSchemaGenerator implements ToolSchemaGenerator {
        @Override
        public String generate() {
            return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"Search query\"},\"limit\":{\"type\":\"integer\",\"description\":\"Max results\"}},\"required\":[\"query\"]}";
        }
    }

    public static class SearchOutputSchemaGenerator implements ToolSchemaGenerator {
        @Override
        public String generate() {
            return "{\"type\":\"object\",\"properties\":{\"count\":{\"type\":\"integer\"},\"items\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}";
        }
    }

    @Tool(name = "search", description = "Searches for items matching a query", structuredContent = true)
    @InputSchema(generator = SearchInputSchemaGenerator.class)
    @OutputSchema(generator = SearchOutputSchemaGenerator.class)
    SearchResult search(@ToolArg(description = "Search query") String query, @ToolArg(description = "Max results", required = false) int limit) {
        List<String> items = List.of("result-1", "result-2");
        return new SearchResult(items.size(), items);
    }

    @Tool(name = "add-from", description = "Adds two numbers with output schema from AddResult", structuredContent = true)
    @OutputSchema(from = AddResult.class)
    AddResult addFrom(@ToolArg(description = "First number") int a, @ToolArg(description = "Second number") int b) {
        return new AddResult(a + b, a + " + " + b);
    }
}
