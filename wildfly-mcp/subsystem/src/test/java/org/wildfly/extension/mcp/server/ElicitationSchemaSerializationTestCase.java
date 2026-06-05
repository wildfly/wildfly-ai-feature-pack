/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.Test;
import org.wildfly.extension.mcp.injection.elicitation.BooleanProperty;
import org.wildfly.extension.mcp.injection.elicitation.EnumProperty;
import org.wildfly.extension.mcp.injection.elicitation.IntegerProperty;
import org.wildfly.extension.mcp.injection.elicitation.MultiStringProperty;
import org.wildfly.extension.mcp.injection.elicitation.NumberProperty;
import org.wildfly.extension.mcp.injection.elicitation.StringProperty;

public class ElicitationSchemaSerializationTestCase {

    // ==================== BooleanProperty ====================

    @Test
    public void testBooleanPropertyMinimal() {
        JsonObject json = new BooleanProperty("foo").jsonSchema();
        assertEquals("boolean", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testBooleanPropertyRequired() {
        BooleanProperty property = new BooleanProperty("foo").required(false).defaultValue(false);
        assertFalse(property.required());
        JsonObject json = property.jsonSchema();
        assertEquals("boolean", json.getString("type"));
        assertEquals(false, json.getBoolean("default"));
    }

    // ==================== StringProperty ====================

    @Test
    public void testStringPropertyMinimal() {
        JsonObject json = new StringProperty("foo").jsonSchema();
        assertEquals("string", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testStringPropertyAllFields() {
        StringProperty property = new StringProperty("foo").title("My Title").description("A description").minLength(2).maxLength(100).pattern("^[A-Za-z]+$").format(StringProperty.Format.EMAIL).defaultValue("default@example.com");
        assertEquals("foo", property.name());
        assertTrue(property.required());
        JsonObject json = property.jsonSchema();
        assertEquals("string", json.getString("type"));
        assertEquals("My Title", json.getString("title"));
        assertEquals("A description", json.getString("description"));
        assertEquals(2, json.getInt("minLength"));
        assertEquals(100, json.getInt("maxLength"));
        assertEquals("^[A-Za-z]+$", json.getString("pattern"));
        assertEquals("email", json.getString("format"));
        assertEquals("default@example.com", json.getString("default"));
    }

    @Test
    public void testStringPropertyRequiredOnly() {
        StringProperty property = new StringProperty("foo");
        assertTrue(property.required());
        JsonObject json = property.jsonSchema();
        assertEquals("string", json.getString("type"));
        assertFalse(json.containsKey("title"));
        assertFalse(json.containsKey("description"));
        assertFalse(json.containsKey("minLength"));
        assertFalse(json.containsKey("maxLength"));
        assertFalse(json.containsKey("pattern"));
        assertFalse(json.containsKey("format"));
        assertFalse(json.containsKey("default"));
    }

    @Test
    public void testStringPropertyRequiredWithTitleAndDescription() {
        StringProperty property = new StringProperty("foo").title("Title").description("Desc");
        JsonObject json = property.jsonSchema();
        assertEquals("Title", json.getString("title"));
        assertEquals("Desc", json.getString("description"));
        assertFalse(json.containsKey("format"));
    }

    @Test
    public void testStringPropertyOptionalFieldsAbsent() {
        StringProperty property = new StringProperty("foo").required(false);
        JsonObject json = property.jsonSchema();
        assertEquals("string", json.getString("type"));
        assertEquals(1, json.size());
    }

    // ==================== NumberProperty ====================

    @Test
    public void testNumberPropertyMinimal() {
        JsonObject json = new NumberProperty("foo").jsonSchema();
        assertEquals("number", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testNumberPropertyWithBounds() {
        NumberProperty property = new NumberProperty("foo").min(0.5).max(99.9);
        assertTrue(property.required());
        JsonObject json = property.jsonSchema();
        assertEquals("number", json.getString("type"));
        assertEquals(0.5, json.getJsonNumber("minimum").doubleValue(), 0.001);
        assertEquals(99.9, json.getJsonNumber("maximum").doubleValue(), 0.001);
    }

    @Test
    public void testNumberPropertyWithAllParameters() {
        NumberProperty property = new NumberProperty("foo").required(false).title("Number").description("title for the number").min(0.5).max(99.9).defaultValue(3.14);
        assertFalse(property.required());
        JsonObject json = property.jsonSchema();
        assertEquals("number", json.getString("type"));
        assertEquals("Number", json.getString("title"));
        assertEquals("title for the number", json.getString("description"));
        assertEquals(0.5, json.getJsonNumber("minimum").doubleValue(), 0.001);
        assertEquals(99.9, json.getJsonNumber("maximum").doubleValue(), 0.001);
        assertEquals(3.14, json.getJsonNumber("default").doubleValue(), 0.001);
    }

    @Test
    public void testNumberPropertyOnlyMin() {
        NumberProperty property = new NumberProperty("foo").min(1.0);
        JsonObject json = property.jsonSchema();
        assertEquals("number", json.getString("type"));
        assertTrue(json.containsKey("minimum"));
        assertFalse(json.containsKey("maximum"));
    }

    // ==================== IntegerProperty ====================

    @Test
    public void testIntegerPropertyMinimal() {
        JsonObject json = new IntegerProperty("foo").jsonSchema();
        assertEquals("integer", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testIntegerPropertyWithBounds() {
        IntegerProperty property = new IntegerProperty("foo").min(1).max(10);
        JsonObject json = property.jsonSchema();
        assertEquals("integer", json.getString("type"));
        assertEquals(1, json.getInt("minimum"));
        assertEquals(10, json.getInt("maximum"));
    }

    @Test
    public void testIntegerPropertyOnlyMax() {
        IntegerProperty property = new IntegerProperty("foo").required(false).max(100);
        JsonObject json = property.jsonSchema();
        assertFalse(json.containsKey("minimum"));
        assertEquals(100, json.getInt("maximum"));
    }

    // ==================== EnumProperty (single-select) ====================

    @Test
    public void testEnumPropertyWithoutTitles() {
        EnumProperty property = new EnumProperty("foo", List.of("A", "B", "C"));
        JsonObject json = property.jsonSchema();
        assertEquals("string", json.getString("type"));
        JsonArray arr = json.getJsonArray("enum");
        assertNotNull(arr);
        assertEquals(3, arr.size());
        assertEquals("A", arr.getString(0));
        assertEquals("B", arr.getString(1));
        assertEquals("C", arr.getString(2));
        assertFalse(json.containsKey("oneOf"));
        assertTrue(property.required());
    }

    @Test
    public void testEnumPropertyWithTitles() {
        EnumProperty property = new EnumProperty("foo", List.of("en", "fr")).required(false).enumTitles(List.of("English", "French"));
        JsonObject json = property.jsonSchema();
        assertEquals("string", json.getString("type"));
        assertFalse(json.containsKey("enum"));
        JsonArray oneOf = json.getJsonArray("oneOf");
        assertNotNull(oneOf);
        assertEquals(2, oneOf.size());
        assertEquals("en", oneOf.getJsonObject(0).getString("const"));
        assertEquals("English", oneOf.getJsonObject(0).getString("title"));
        assertEquals("fr", oneOf.getJsonObject(1).getString("const"));
        assertEquals("French", oneOf.getJsonObject(1).getString("title"));
    }

    @Test
    public void testEnumPropertyWithDefault() {
        EnumProperty property = new EnumProperty("foo", List.of("Red", "Green", "Blue")).defaultValue("Red");
        JsonObject json = property.jsonSchema();
        assertEquals("Red", json.getString("default"));
        assertEquals(3, json.getJsonArray("enum").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumPropertyEmptyValuesThrows() {
        new EnumProperty("foo", List.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumPropertyMismatchedTitlesThrows() {
        new EnumProperty("foo", List.of("a", "b")).enumTitles(List.of("Only One"));
    }

    // ==================== MultiStringProperty (multi-select) ====================

    @Test
    public void testMultiStringPropertyWithoutTitles() {
        MultiStringProperty property = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"));
        JsonObject json = property.jsonSchema();
        assertEquals("array", json.getString("type"));
        JsonObject items = json.getJsonObject("items");
        assertNotNull(items);
        assertEquals("string", items.getString("type"));
        JsonArray enumArr = items.getJsonArray("enum");
        assertEquals(3, enumArr.size());
        assertEquals("Red", enumArr.getString(0));
        assertEquals("Green", enumArr.getString(1));
        assertEquals("Blue", enumArr.getString(2));
        assertFalse(json.containsKey("minItems"));
        assertFalse(json.containsKey("maxItems"));
        assertTrue(property.required());
    }

    @Test
    public void testMultiStringPropertyWithTitles() {
        MultiStringProperty property = new MultiStringProperty("colors", List.of("#FF0000", "#00FF00", "#0000FF"))
                .required(false).enumTitles(List.of("Red", "Green", "Blue"));
        JsonObject json = property.jsonSchema();
        assertEquals("array", json.getString("type"));
        JsonObject items = json.getJsonObject("items");
        assertFalse(items.containsKey("enum"));
        JsonArray anyOf = items.getJsonArray("anyOf");
        assertNotNull(anyOf);
        assertEquals(3, anyOf.size());
        assertEquals("#FF0000", anyOf.getJsonObject(0).getString("const"));
        assertEquals("Red", anyOf.getJsonObject(0).getString("title"));
        assertEquals("#0000FF", anyOf.getJsonObject(2).getString("const"));
        assertEquals("Blue", anyOf.getJsonObject(2).getString("title"));
    }

    @Test
    public void testMultiStringPropertyWithBoundsAndDefault() {
        MultiStringProperty property = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"))
                .description("Choose colors").minItems(1).maxItems(2).defaultValue(List.of("Red", "Green"));
        JsonObject json = property.jsonSchema();
        assertEquals("array", json.getString("type"));
        assertEquals("Choose colors", json.getString("description"));
        assertEquals(1, json.getInt("minItems"));
        assertEquals(2, json.getInt("maxItems"));
        JsonArray defaults = json.getJsonArray("default");
        assertNotNull(defaults);
        assertEquals(2, defaults.size());
        assertEquals("Red", defaults.getString(0));
        assertEquals("Green", defaults.getString(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultiStringPropertyEmptyValuesThrows() {
        new MultiStringProperty("foo", List.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultiStringPropertyMismatchedTitlesThrows() {
        new MultiStringProperty("foo", List.of("a", "b")).enumTitles(List.of("Only One"));
    }
}
