/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata.Kind;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

class WildFlyMCPRegistryTestCase {

    private WildFlyMCPRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WildFlyMCPRegistry();
    }

    private static MCPFeatureMetadata metadata(Kind kind, String name) {
        MethodMetadata method = new MethodMetadata("doIt", "A description", null, null, List.of(), "com.example.Foo", "java.lang.String");
        return new MCPFeatureMetadata(kind, name, method);
    }

    // --- Tool name validation ---

    @ParameterizedTest(name = "valid tool name: {0}")
    @ValueSource(strings = {"my_tool", "my-tool", "my.tool", "Tool123", "a", "A_b-c.d"})
    void validToolNames(String name) {
        registry.addTool(name, metadata(Kind.TOOL, name));
        assertNotNull(registry.getTool(name));
    }

    @ParameterizedTest(name = "invalid tool name rejected: {0}")
    @ValueSource(strings = {"", "has space", "tool!", "tool@name", "a/b", "tool#1"})
    void invalidToolNamesRejected(String name) {
        assertThrows(IllegalArgumentException.class, () -> registry.addTool(name, metadata(Kind.TOOL, name)));
    }

    @Test
    void toolNameMaxLength128() {
        String maxName = "a".repeat(128);
        registry.addTool(maxName, metadata(Kind.TOOL, maxName));
        assertNotNull(registry.getTool(maxName));
    }

    @Test
    void toolNameOver128Rejected() {
        String tooLong = "a".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> registry.addTool(tooLong, metadata(Kind.TOOL, tooLong)));
    }

    // --- Add and retrieve tools ---

    @Test
    void addAndGetTool() {
        MCPFeatureMetadata meta = metadata(Kind.TOOL, "myTool");
        registry.addTool("myTool", meta);
        assertEquals(meta, registry.getTool("myTool"));
    }

    @Test
    void getToolReturnsNullForUnknown() {
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    void listToolsReturnsAllAdded() {
        registry.addTool("tool1", metadata(Kind.TOOL, "tool1"));
        registry.addTool("tool2", metadata(Kind.TOOL, "tool2"));
        int count = 0;
        for (MCPFeatureMetadata ignored : registry.listTools()) {
            count++;
        }
        assertEquals(2, count);
    }

    // --- Prompts ---

    @Test
    void addAndGetPrompt() {
        MCPFeatureMetadata meta = metadata(Kind.PROMPT, "greet");
        registry.addPrompt("greet", meta);
        assertEquals(meta, registry.getPrompt("greet"));
    }

    @Test
    void getPromptReturnsNullForUnknown() {
        assertNull(registry.getPrompt("nonexistent"));
    }

    @Test
    void listPromptsReturnsAllAdded() {
        registry.addPrompt("p1", metadata(Kind.PROMPT, "p1"));
        registry.addPrompt("p2", metadata(Kind.PROMPT, "p2"));
        int count = 0;
        for (MCPFeatureMetadata ignored : registry.listPrompts()) {
            count++;
        }
        assertEquals(2, count);
    }

    // --- Resources ---

    @Test
    void addAndGetResource() {
        MCPFeatureMetadata meta = metadata(Kind.RESOURCE, "config");
        registry.addResource("file:///config.json", meta);
        assertEquals(meta, registry.getResource("file:///config.json"));
    }

    @Test
    void getResourceReturnsNullForUnknown() {
        assertNull(registry.getResource("nonexistent"));
    }

    @Test
    void listResourcesReturnsAllAdded() {
        registry.addResource("r1", metadata(Kind.RESOURCE, "r1"));
        registry.addResource("r2", metadata(Kind.RESOURCE, "r2"));
        int count = 0;
        for (MCPFeatureMetadata ignored : registry.listResources()) {
            count++;
        }
        assertEquals(2, count);
    }

    // --- Resource templates ---

    @Test
    void addAndGetResourceTemplate() {
        MCPFeatureMetadata meta = metadata(Kind.RESOURCE_TEMPLATE, "userTemplate");
        registry.addResourceTemplate("users/{id}", meta);
        assertEquals(meta, registry.getResourceTemplate("users/{id}"));
    }

    @Test
    void findResourceTemplateByUri() {
        MCPFeatureMetadata meta = metadata(Kind.RESOURCE_TEMPLATE, "userTemplate");
        registry.addResourceTemplate("users/{id}", meta);
        MCPFeatureMetadata found = registry.findResourceTemplateByUri("users/42");
        assertEquals(meta, found);
    }

    @Test
    void findResourceTemplateByUriMultipleParams() {
        MCPFeatureMetadata meta = metadata(Kind.RESOURCE_TEMPLATE, "itemTemplate");
        registry.addResourceTemplate("org/{org}/repo/{repo}", meta);
        assertEquals(meta, registry.findResourceTemplateByUri("org/acme/repo/widgets"));
    }

    @Test
    void findResourceTemplateByUriNoMatch() {
        registry.addResourceTemplate("users/{id}", metadata(Kind.RESOURCE_TEMPLATE, "t"));
        assertNull(registry.findResourceTemplateByUri("orders/42"));
    }

    @Test
    void findResourceTemplateByUriReturnsNullWhenEmpty() {
        assertNull(registry.findResourceTemplateByUri("anything"));
    }

    // --- Completions ---

    @Test
    void addAndGetPromptCompletion() {
        MCPFeatureMetadata meta = metadata(Kind.PROMPT_COMPLETE, "pc");
        registry.addPromptCompletion("key1", meta);
        assertEquals(meta, registry.getPromptCompletion("key1"));
    }

    @Test
    void addAndGetResourceTemplateCompletion() {
        MCPFeatureMetadata meta = metadata(Kind.RESOURCE_TEMPLATE_COMPLETE, "rtc");
        registry.addResourceTemplateCompletion("key2", meta);
        assertEquals(meta, registry.getResourceTemplateCompletion("key2"));
    }

    // --- TOOL_NAME_PATTERN ---

    @Test
    void toolNamePatternMatchesDotHyphenUnderscore() {
        assertTrue(WildFlyMCPRegistry.TOOL_NAME_PATTERN.matcher("a.b-c_d").matches());
    }

    @Test
    void toolNamePatternRejectsSlash() {
        assertFalse(WildFlyMCPRegistry.TOOL_NAME_PATTERN.matcher("a/b").matches());
    }
}
