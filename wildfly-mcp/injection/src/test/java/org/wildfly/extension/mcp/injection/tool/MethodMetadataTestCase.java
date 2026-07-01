/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.injection.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class MethodMetadataTestCase {

    @Test
    void argumentTypesReturnsCorrectArray() {
        List<ArgumentMetadata> args = List.of(
                new ArgumentMetadata("name", "The name", true, String.class),
                new ArgumentMetadata("count", "How many", false, int.class));
        MethodMetadata method = new MethodMetadata("doStuff", "desc", null, null, args, "com.example.Foo", "void");
        Class<?>[] types = method.argumentTypes();
        assertArrayEquals(new Class<?>[]{String.class, int.class}, types);
    }

    @Test
    void argumentTypesEmptyList() {
        MethodMetadata method = new MethodMetadata("doStuff", "desc", null, null, List.of(), "com.example.Foo", "void");
        Class<?>[] types = method.argumentTypes();
        assertEquals(0, types.length);
    }

    @Test
    void argumentTypesSingleArg() {
        List<ArgumentMetadata> args = List.of(new ArgumentMetadata("id", "ID", true, long.class));
        MethodMetadata method = new MethodMetadata("get", "desc", null, null, args, "com.example.Foo", "java.lang.Object");
        assertArrayEquals(new Class<?>[]{long.class}, method.argumentTypes());
    }

    @Test
    void recordAccessors() {
        List<ArgumentMetadata> args = List.of(new ArgumentMetadata("x", "val", true, double.class));
        MethodMetadata method = new MethodMetadata("calc", "Calculate", "calc://run", "application/json", args, "com.example.Calc", "double");
        assertEquals("calc", method.name());
        assertEquals("Calculate", method.description());
        assertEquals("calc://run", method.uri());
        assertEquals("application/json", method.mimeType());
        assertEquals(1, method.arguments().size());
        assertEquals("com.example.Calc", method.declaringClassName());
        assertEquals("double", method.returnType());
    }
}
