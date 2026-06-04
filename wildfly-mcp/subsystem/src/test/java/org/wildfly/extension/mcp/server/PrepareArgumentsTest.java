/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.wildfly.extension.mcp.injection.tool.ArgumentMetadata;
import org.wildfly.extension.mcp.injection.tool.MCPFeatureMetadata;
import org.wildfly.extension.mcp.injection.tool.MethodMetadata;

public class PrepareArgumentsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    enum Color { RED, GREEN, BLUE }

    public static class Point {
        public int x;
        public int y;
    }

    private static class TypeHolder {
        public List<String> stringList;
    }

    private MCPFeatureMetadata makeMetadata(List<ArgumentMetadata> args) {
        return new MCPFeatureMetadata(MCPFeatureMetadata.Kind.TOOL, "test",
                new MethodMetadata("test", "test", null, null, args, "TestClass", "void"));
    }

    @Test
    public void testEmptyArguments() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of());
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(), Map.of(), MAPPER);
        assertEquals(0, result.length);
    }

    @Test
    public void testStringArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("name", "", true, String.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("name", Json.createValue("hello")), MAPPER);
        assertEquals("hello", result[0]);
    }

    @Test
    public void testIntArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("count", "", true, int.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("count", Json.createValue(42)), MAPPER);
        assertEquals(42, result[0]);
    }

    @Test
    public void testBooleanArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("flag", "", true, boolean.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("flag", JsonValue.TRUE), MAPPER);
        assertEquals(true, result[0]);
    }

    @Test
    public void testDoubleArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("value", "", true, double.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("value", Json.createValue(3.14)), MAPPER);
        assertEquals(3.14, (double) result[0], 0.001);
    }

    @Test
    public void testEnumArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("color", "", true, Color.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("color", Json.createValue("GREEN")), MAPPER);
        assertEquals(Color.GREEN, result[0]);
    }

    @Test
    public void testListStringArgument() throws Exception {
        Type listStringType = TypeHolder.class.getDeclaredField("stringList").getGenericType();
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("items", "", true, listStringType)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("items", Json.createArrayBuilder().add("a").add("b").build()), MAPPER);
        assertEquals(List.of("a", "b"), result[0]);
    }

    @Test
    public void testPojoArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("point", "", true, Point.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(),
                Map.of("point", Json.createObjectBuilder().add("x", 1).add("y", 2).build()), MAPPER);
        Point p = (Point) result[0];
        assertEquals(1, p.x);
        assertEquals(2, p.y);
    }

    @Test
    public void testMissingRequiredArgument() {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("required", "", true, String.class)));
        assertThrows(MCPException.class, () ->
                MCPServerUtils.prepareArguments(metadata.arguments(), Map.of(), MAPPER));
    }

    @Test
    public void testOptionalNullArgument() throws Exception {
        MCPFeatureMetadata metadata = makeMetadata(List.of(
                new ArgumentMetadata("optional", "", false, String.class)));
        Object[] result = MCPServerUtils.prepareArguments(metadata.arguments(), Map.of(), MAPPER);
        assertNull(result[0]);
    }
}
