/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;
import org.wildfly.extension.mcp.injection.WildFlyMCPRegistry;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class ToolNameValidationTestCase {

    private static MCPFeatureMetadata dummyTool(String name) {
        return MCPFeatureMetadata.builder(
                MCPFeatureMetadata.Kind.TOOL, name,
                new MethodMetadata(name, "A tool", null, null, List.of(), "org.test.Tool", "java.lang.String")).build();
    }

    // ==================== Valid Names ====================

    @Test
    public void testValidSimpleName() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("echo", dummyTool("echo"));
    }

    @Test
    public void testValidNameWithUnderscore() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("my_tool", dummyTool("my_tool"));
    }

    @Test
    public void testValidNameWithHyphen() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("my-tool", dummyTool("my-tool"));
    }

    @Test
    public void testValidNameWithDot() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("my.tool", dummyTool("my.tool"));
    }

    @Test
    public void testValidNameWithMixedCase() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        registry.addTool("MyTool_v2.0-beta", dummyTool("MyTool_v2.0-beta"));
    }

    @Test
    public void testValidNameMaxLength() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        String name = "a".repeat(128);
        registry.addTool(name, dummyTool(name));
    }

    // ==================== Invalid Names ====================

    @Test
    public void testEmptyNameRejected() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.addTool("", dummyTool("")));
    }

    @Test
    public void testNameWithSpaceRejected() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.addTool("my tool", dummyTool("my tool")));
    }

    @Test
    public void testNameWithSpecialCharRejected() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.addTool("my@tool", dummyTool("my@tool")));
    }

    @Test
    public void testNameWithSlashRejected() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.addTool("tools/call", dummyTool("tools/call")));
    }

    @Test
    public void testNameExceeding128CharsRejected() {
        WildFlyMCPRegistry registry = new WildFlyMCPRegistry();
        String name = "a".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> registry.addTool(name, dummyTool(name)));
    }
}
