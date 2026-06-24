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
import org.junit.Test;
import org.wildfly.mcp.model.Annotations;
import org.wildfly.mcp.model.Role;

public class ResourceAnnotationsUtilTestCase {

    @Test
    public void testNullAnnotationsAddsNothing() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, null);
        JsonObject result = builder.build();
        assertFalse(result.containsKey("annotations"));
    }

    @Test
    public void testAnnotationsWithAllEmptyFieldsAddsNothing() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, Annotations.builder().build());
        JsonObject result = builder.build();
        assertFalse(result.containsKey("annotations"));
    }

    @Test
    public void testAudienceOnly() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder, Annotations.builder().setAudience(Role.USER).build());
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
        ResourceAnnotationsUtil.addAnnotations(builder, Annotations.builder().setPriority(0.8).build());
        JsonObject result = builder.build();
        assertTrue(result.containsKey("annotations"));
        JsonObject annotations = result.getJsonObject("annotations");
        assertFalse(annotations.containsKey("audience"));
        assertEquals(0.8, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }

    @Test
    public void testBothAudienceAndPriority() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ResourceAnnotationsUtil.addAnnotations(builder,
                Annotations.builder().setAudience(Role.ASSISTANT).setPriority(0.5).build());
        JsonObject result = builder.build();
        assertTrue(result.containsKey("annotations"));
        JsonObject annotations = result.getJsonObject("annotations");
        assertEquals("assistant", annotations.getJsonArray("audience").getString(0));
        assertEquals(0.5, annotations.getJsonNumber("priority").doubleValue(), 0.001);
    }
}
