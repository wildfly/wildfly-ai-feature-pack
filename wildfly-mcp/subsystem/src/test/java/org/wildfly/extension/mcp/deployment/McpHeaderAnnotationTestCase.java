/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.mcp.api.McpHeader;

class McpHeaderAnnotationTestCase {

    @Test
    void headerParamRecognized() throws IOException {
        MCPFeatureMetadata metadata = indexAndGetTool(HeaderFixtures.class, "withHeader");

        List<ArgumentMetadata> args = metadata.arguments();
        assertEquals(2, args.size());

        ArgumentMetadata headerArg = args.get(0);
        assertTrue(headerArg.isHeader());
        assertEquals("auth-token", headerArg.headerName());
        assertEquals("token", headerArg.name());
        assertFalse(headerArg.required());

        ArgumentMetadata regularArg = args.get(1);
        assertFalse(regularArg.isHeader());
        assertNull(regularArg.headerName());
        assertEquals("message", regularArg.name());
    }

    @Test
    void requiredHeaderParamRecognized() throws IOException {
        MCPFeatureMetadata metadata = indexAndGetTool(HeaderFixtures.class, "withRequiredHeader");

        List<ArgumentMetadata> args = metadata.arguments();
        assertEquals(1, args.size());

        ArgumentMetadata headerArg = args.get(0);
        assertTrue(headerArg.isHeader());
        assertEquals("api-key", headerArg.headerName());
        assertTrue(headerArg.required());
    }

    @Test
    void headerOnlyTool() throws IOException {
        MCPFeatureMetadata metadata = indexAndGetTool(HeaderFixtures.class, "headerOnly");

        List<ArgumentMetadata> args = metadata.arguments();
        assertEquals(1, args.size());

        ArgumentMetadata headerArg = args.get(0);
        assertTrue(headerArg.isHeader());
        assertEquals("tenant", headerArg.headerName());
    }

    @Test
    void noHeaderParamsUnchanged() throws IOException {
        MCPFeatureMetadata metadata = indexAndGetTool(HeaderFixtures.class, "noHeaders");

        List<ArgumentMetadata> args = metadata.arguments();
        assertEquals(1, args.size());
        assertFalse(args.get(0).isHeader());
    }

    @Test
    void multipleHeaders() throws IOException {
        MCPFeatureMetadata metadata = indexAndGetTool(HeaderFixtures.class, "multipleHeaders");

        List<ArgumentMetadata> args = metadata.arguments();
        assertEquals(3, args.size());

        assertTrue(args.get(0).isHeader());
        assertEquals("token", args.get(0).headerName());

        assertFalse(args.get(1).isHeader());
        assertEquals("data", args.get(1).name());

        assertTrue(args.get(2).isHeader());
        assertEquals("tenant-id", args.get(2).headerName());
    }

    // ==================== Helpers ====================

    private MCPFeatureMetadata indexAndGetTool(Class<?> fixtureClass, String toolName) throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(fixtureClass);
        indexer.indexClass(Tool.class);
        indexer.indexClass(ToolArg.class);
        indexer.indexClass(McpHeader.class);
        Index index = indexer.complete();

        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        MCPServerDependencyProcessor processor = new MCPServerDependencyProcessor();
        List<org.jboss.jandex.AnnotationInstance> toolAnnotations =
                index.getAnnotations(DotName.createSimple(Tool.class));

        processor.processTools(registry, toolAnnotations);
        MCPFeatureMetadata metadata = registry.getTool(toolName);
        if (metadata == null) {
            throw new AssertionError("Tool not found in registry: " + toolName);
        }
        return metadata;
    }

    @SuppressWarnings("unused")
    static class HeaderFixtures {

        @Tool(name = "withHeader", description = "Tool with a header param")
        String withHeader(@McpHeader("auth-token") String token,
                          @ToolArg(description = "The message") String message) {
            return token + ": " + message;
        }

        @Tool(name = "withRequiredHeader", description = "Tool with a required header")
        String withRequiredHeader(@McpHeader(value = "api-key", required = true) String apiKey) {
            return apiKey;
        }

        @Tool(name = "headerOnly", description = "Tool with only header params")
        String headerOnly(@McpHeader("tenant") String tenant) {
            return tenant;
        }

        @Tool(name = "noHeaders", description = "Tool without headers")
        String noHeaders(@ToolArg(description = "Input") String input) {
            return input;
        }

        @Tool(name = "multipleHeaders", description = "Tool with multiple headers and args")
        String multipleHeaders(@McpHeader("token") String token,
                               @ToolArg(description = "Data") String data,
                               @McpHeader("tenant-id") String tenantId) {
            return token + "/" + data + "/" + tenantId;
        }
    }
}
