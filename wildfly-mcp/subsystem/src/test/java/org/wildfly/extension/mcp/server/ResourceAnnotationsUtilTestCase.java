/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.Test;
import org.mcpjava.server.Role;

public class ResourceAnnotationsUtilTestCase {

    @Test
    public void testEmptyAnnotationsAddsNothing() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, Optional.empty(), OptionalDouble.empty());
        JsonObject result = builder.build();
        assertFalse(result.containsKey("annotations"));
    }

    @Test
    public void testAudienceOnly() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, Optional.of(Set.of(Role.USER)), OptionalDouble.empty());
        JsonObject result = builder.build();
        assertTrue(result.containsKey("annotations"));
        JsonObject annotations = result.getJsonObject("annotations");
        assertEquals(1, annotations.getJsonArray("audience").size());
        assertEquals("user", annotations.getJsonArray("audience").getString(0));
        assertFalse(annotations.containsKey("priority"));
    }

    @Test
    public void testPriorityOnly() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, Optional.empty(), OptionalDouble.of(0.8));
        JsonObject result = builder.build();
        assertTrue(result.containsKey("annotations"));
        JsonObject annotations = result.getJsonObject("annotations");
        assertFalse(annotations.containsKey("audience"));
        assertEquals(0.8, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }

    @Test
    public void testBothAudienceAndPriority() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, Optional.of(Set.of(Role.ASSISTANT)), OptionalDouble.of(0.5));
        JsonObject result = builder.build();
        assertTrue(result.containsKey("annotations"));
        JsonObject annotations = result.getJsonObject("annotations");
        assertEquals("assistant", annotations.getJsonArray("audience").getString(0));
        assertEquals(0.5, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }
}
