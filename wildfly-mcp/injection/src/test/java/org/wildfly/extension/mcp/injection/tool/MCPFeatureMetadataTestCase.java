/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mcpjava.server.Role;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata.Kind;

class MCPFeatureMetadataTestCase {

    private static MethodMetadata method(String name, String description, List<ArgumentMetadata> args) {
        return new MethodMetadata(name, description, null, null, args, "com.example.Foo", "java.lang.String");
    }

    @Test
    void minimalConstructorSetsDefaults() {
        MCPFeatureMetadata meta = new MCPFeatureMetadata(Kind.TOOL, "myTool", method("run", "desc", List.of()));
        assertEquals(Kind.TOOL, meta.kind());
        assertEquals("myTool", meta.name());
        assertNull(meta.toolAnnotations());
        assertFalse(meta.structuredContent());
        assertTrue(meta.inputSchemaGenerator().isEmpty());
        assertTrue(meta.outputSchemaGenerator().isEmpty());
        assertTrue(meta.outputSchemaFrom().isEmpty());
        assertNull(meta.title());
        assertEquals(-1, meta.size());
        assertTrue(meta.audience().isEmpty());
        assertTrue(meta.priority().isEmpty());
    }

    @Test
    void toolConstructorSetsDefaults() {
        MCPFeatureMetadata meta = new MCPFeatureMetadata(Kind.TOOL, "myTool",
                method("run", "desc", List.of()),
                new ToolAnnotations("Tool Title", null, true, null, null),
                true, Optional.of("gen.Input"), Optional.of("gen.Output"), Optional.empty());
        assertEquals("Tool Title", meta.toolAnnotations().title());
        assertTrue(meta.structuredContent());
        assertEquals("gen.Input", meta.inputSchemaGenerator().get());
        assertEquals("gen.Output", meta.outputSchemaGenerator().get());
        assertNull(meta.title());
        assertEquals(-1, meta.size());
    }

    @Test
    void resourceConstructorSetsDefaults() {
        MCPFeatureMetadata meta = new MCPFeatureMetadata(Kind.RESOURCE, "config",
                method("getConfig", "desc", List.of()),
                "Config Resource", 1024, Optional.of(Set.of(Role.ASSISTANT)), OptionalDouble.of(0.5));
        assertEquals("Config Resource", meta.title());
        assertEquals(1024, meta.size());
        assertTrue(meta.audience().isPresent());
        assertEquals(0.5, meta.priority().getAsDouble());
        assertNull(meta.toolAnnotations());
        assertFalse(meta.structuredContent());
    }

    @Test
    void descriptionDelegatesToMethod() {
        MCPFeatureMetadata meta = new MCPFeatureMetadata(Kind.PROMPT, "greet", method("greet", "Says hello", List.of()));
        assertEquals("Says hello", meta.description());
    }

    @Test
    void argumentsDelegatesToMethod() {
        List<ArgumentMetadata> args = List.of(
                new ArgumentMetadata("name", "The name", true, String.class),
                new ArgumentMetadata("age", "The age", false, int.class));
        MCPFeatureMetadata meta = new MCPFeatureMetadata(Kind.TOOL, "greet", method("greet", "desc", args));
        assertEquals(2, meta.arguments().size());
        assertEquals("name", meta.arguments().get(0).name());
        assertEquals("age", meta.arguments().get(1).name());
    }

    @Test
    void kindEnumValues() {
        Kind[] values = Kind.values();
        assertEquals(6, values.length);
        assertEquals(Kind.PROMPT, Kind.valueOf("PROMPT"));
        assertEquals(Kind.TOOL, Kind.valueOf("TOOL"));
        assertEquals(Kind.RESOURCE, Kind.valueOf("RESOURCE"));
        assertEquals(Kind.RESOURCE_TEMPLATE, Kind.valueOf("RESOURCE_TEMPLATE"));
        assertEquals(Kind.PROMPT_COMPLETE, Kind.valueOf("PROMPT_COMPLETE"));
        assertEquals(Kind.RESOURCE_TEMPLATE_COMPLETE, Kind.valueOf("RESOURCE_TEMPLATE_COMPLETE"));
    }
}
