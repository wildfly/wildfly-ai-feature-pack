/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.mcp.injection.elicitation.Elicitation.Response.Action.ACCEPT;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.wildfly.extension.mcp.injection.elicitation.Elicitation;
import org.wildfly.extension.mcp.injection.elicitation.MultiStringProperty;

public class ElicitationResponseTestCase {

    // ==================== getStrings(MultiStringProperty) ====================

    @Test
    public void testGetStringsWithPropertyReturnsValue() {
        MultiStringProperty colors = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"));
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT,
                Map.of("colors", List.of("Red", "Blue")));

        Optional<List<String>> result = response.getStrings(colors);
        assertTrue(result.isPresent());
        assertEquals(List.of("Red", "Blue"), result.get());
    }

    @Test
    public void testGetStringsWithPropertyFallsBackToDefault() {
        MultiStringProperty colors = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"))
                .defaultValue(List.of("Red"));
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT, Map.of());

        Optional<List<String>> result = response.getStrings(colors);
        assertTrue(result.isPresent());
        assertEquals(List.of("Red"), result.get());
    }

    @Test
    public void testGetStringsWithPropertyReturnsEmptyWhenNoValueAndNoDefault() {
        MultiStringProperty colors = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"));
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT, Map.of());

        Optional<List<String>> result = response.getStrings(colors);
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetStringsWithPropertyValueOverridesDefault() {
        MultiStringProperty colors = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"))
                .defaultValue(List.of("Red"));
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT,
                Map.of("colors", List.of("Green", "Blue")));

        Optional<List<String>> result = response.getStrings(colors);
        assertTrue(result.isPresent());
        assertEquals(List.of("Green", "Blue"), result.get());
    }

    @Test
    public void testGetStringsWithStringKeyReturnsValue() {
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT,
                Map.of("tags", List.of("a", "b", "c")));

        Optional<List<String>> result = response.getStrings("tags");
        assertTrue(result.isPresent());
        assertEquals(List.of("a", "b", "c"), result.get());
    }

    @Test
    public void testGetStringsWithStringKeyReturnsEmptyWhenAbsent() {
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT, Map.of());

        assertFalse(response.getStrings("tags").isPresent());
    }

    @Test
    public void testGetStringsWithNullContent() {
        Elicitation.Response response = new Elicitation.Response(
                ACCEPT, null);

        MultiStringProperty colors = new MultiStringProperty("colors", List.of("Red", "Green"));
        assertFalse(response.getStrings(colors).isPresent());
        assertFalse(response.getStrings("colors").isPresent());
    }
}
