/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ElicitationSchemaSerializationTestCase {

    // ==================== BooleanProperty ====================

    @Test
    public void testBooleanPropertyMinimal() {
        JsonObject json = new BooleanProperty("foo").jsonSchema();
        assertEquals("boolean", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testBooleanPropertyAccessors() {
        BooleanProperty property = new BooleanProperty("flag")
                .title("My Flag").description("A flag").defaultValue(true);
        assertEquals("flag", property.name());
        assertTrue(property.required());
        assertEquals("My Flag", property.title());
        assertEquals("A flag", property.description());
        assertEquals(true, property.defaultValue());
    }

    @Test
    public void testBooleanPropertyOptional() {
        BooleanProperty property = new BooleanProperty("foo").optional();
        assertFalse(property.required());
    }

    @Test
    public void testBooleanPropertyRequired() {
        BooleanProperty property = new BooleanProperty("foo").required(false).defaultValue(false);
        assertFalse(property.required());
        JsonObject json = property.jsonSchema();
        assertEquals("boolean", json.getString("type"));
        assertEquals(false, json.getBoolean("default"));
    }

    @Test
    public void testBooleanPropertyAllFieldsInJsonSchema() {
        BooleanProperty property = new BooleanProperty("foo")
                .title("Accept").description("Accept terms").defaultValue(false);
        JsonObject json = property.jsonSchema();
        assertEquals("boolean", json.getString("type"));
        assertEquals("Accept", json.getString("title"));
        assertEquals("Accept terms", json.getString("description"));
        assertEquals(false, json.getBoolean("default"));
    }

    @Test
    public void testBooleanPropertyDefaultsAbsentInJsonSchema() {
        BooleanProperty property = new BooleanProperty("foo");
        assertNull(property.title());
        assertNull(property.description());
        assertNull(property.defaultValue());
        JsonObject json = property.jsonSchema();
        assertFalse(json.containsKey("title"));
        assertFalse(json.containsKey("description"));
        assertFalse(json.containsKey("default"));
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
        assertEquals("My Title", property.title());
        assertEquals("A description", property.description());
        assertEquals(2, property.minLength());
        assertEquals(100, property.maxLength());
        assertEquals("^[A-Za-z]+$", property.pattern());
        assertEquals(StringProperty.Format.EMAIL, property.format());
        assertEquals("default@example.com", property.defaultValue());
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
    public void testStringPropertyOptional() {
        StringProperty property = new StringProperty("foo").optional();
        assertFalse(property.required());
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

    @Test
    public void testStringPropertyFormatUri() {
        JsonObject json = new StringProperty("url").format(StringProperty.Format.URI).jsonSchema();
        assertEquals("uri", json.getString("format"));
    }

    @Test
    public void testStringPropertyFormatDate() {
        JsonObject json = new StringProperty("dob").format(StringProperty.Format.DATE).jsonSchema();
        assertEquals("date", json.getString("format"));
    }

    @Test
    public void testStringPropertyFormatDateTime() {
        JsonObject json = new StringProperty("ts").format(StringProperty.Format.DATE_TIME).jsonSchema();
        assertEquals("date-time", json.getString("format"));
    }

    @Test
    public void testStringPropertyFormatValues() {
        assertEquals("email", StringProperty.Format.EMAIL.value());
        assertEquals("uri", StringProperty.Format.URI.value());
        assertEquals("date", StringProperty.Format.DATE.value());
        assertEquals("date-time", StringProperty.Format.DATE_TIME.value());
    }

    // ==================== NumberProperty ====================

    @Test
    public void testNumberPropertyMinimal() {
        JsonObject json = new NumberProperty("foo").jsonSchema();
        assertEquals("number", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testNumberPropertyAccessors() {
        NumberProperty property = new NumberProperty("score")
                .title("Score").description("Player score").min(0.0).max(100.0).defaultValue(50.0);
        assertEquals("score", property.name());
        assertTrue(property.required());
        assertEquals("Score", property.title());
        assertEquals("Player score", property.description());
        assertEquals(0.0, property.min());
        assertEquals(100.0, property.max());
        assertEquals(50.0, property.defaultValue());
    }

    @Test
    public void testNumberPropertyOptional() {
        NumberProperty property = new NumberProperty("foo").optional();
        assertFalse(property.required());
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

    @Test
    public void testNumberPropertyOnlyMax() {
        NumberProperty property = new NumberProperty("foo").max(99.9);
        JsonObject json = property.jsonSchema();
        assertEquals("number", json.getString("type"));
        assertFalse(json.containsKey("minimum"));
        assertEquals(99.9, json.getJsonNumber("maximum").doubleValue(), 0.001);
    }

    @Test
    public void testNumberPropertyDefaultsAbsent() {
        NumberProperty property = new NumberProperty("foo");
        assertNull(property.title());
        assertNull(property.description());
        assertNull(property.min());
        assertNull(property.max());
        assertNull(property.defaultValue());
    }

    // ==================== IntegerProperty ====================

    @Test
    public void testIntegerPropertyMinimal() {
        JsonObject json = new IntegerProperty("foo").jsonSchema();
        assertEquals("integer", json.getString("type"));
        assertEquals(1, json.size());
    }

    @Test
    public void testIntegerPropertyAccessors() {
        IntegerProperty property = new IntegerProperty("count")
                .title("Count").description("Item count").min(0).max(100).defaultValue(10);
        assertEquals("count", property.name());
        assertTrue(property.required());
        assertEquals("Count", property.title());
        assertEquals("Item count", property.description());
        assertEquals(0, property.min());
        assertEquals(100, property.max());
        assertEquals(10, property.defaultValue());
    }

    @Test
    public void testIntegerPropertyOptional() {
        IntegerProperty property = new IntegerProperty("foo").optional();
        assertFalse(property.required());
    }

    @Test
    public void testIntegerPropertyAllFieldsInJsonSchema() {
        IntegerProperty property = new IntegerProperty("age")
                .title("Age").description("Your age").min(0).max(150).defaultValue(25);
        JsonObject json = property.jsonSchema();
        assertEquals("integer", json.getString("type"));
        assertEquals("Age", json.getString("title"));
        assertEquals("Your age", json.getString("description"));
        assertEquals(0, json.getInt("minimum"));
        assertEquals(150, json.getInt("maximum"));
        assertEquals(25, json.getInt("default"));
    }

    @Test
    public void testIntegerPropertyDefaultsAbsent() {
        IntegerProperty property = new IntegerProperty("foo");
        assertNull(property.title());
        assertNull(property.description());
        assertNull(property.min());
        assertNull(property.max());
        assertNull(property.defaultValue());
        JsonObject json = property.jsonSchema();
        assertFalse(json.containsKey("title"));
        assertFalse(json.containsKey("description"));
        assertFalse(json.containsKey("minimum"));
        assertFalse(json.containsKey("maximum"));
        assertFalse(json.containsKey("default"));
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

    @Test
    public void testIntegerPropertyOnlyMin() {
        IntegerProperty property = new IntegerProperty("foo").min(5);
        JsonObject json = property.jsonSchema();
        assertEquals(5, json.getInt("minimum"));
        assertFalse(json.containsKey("maximum"));
    }

    @Test
    public void testIntegerPropertyEqualMinMax() {
        IntegerProperty property = new IntegerProperty("foo").min(5).max(5);
        JsonObject json = property.jsonSchema();
        assertEquals(5, json.getInt("minimum"));
        assertEquals(5, json.getInt("maximum"));
    }

    @Test
    public void testIntegerPropertyMinGreaterThanMaxThrows() {
        assertThrows(IllegalArgumentException.class, () -> new IntegerProperty("foo").max(5).min(10));
    }

    @Test
    public void testIntegerPropertyMaxLessThanMinThrows() {
        assertThrows(IllegalArgumentException.class, () -> new IntegerProperty("foo").min(10).max(5));
    }

    // ==================== EnumProperty (single-select) ====================

    @Test
    public void testEnumPropertyAccessors() {
        EnumProperty property = new EnumProperty("lang", List.of("en", "fr"))
                .title("Language").description("Pick one").enumTitles(List.of("English", "French")).defaultValue("en");
        assertEquals("lang", property.name());
        assertTrue(property.required());
        assertEquals("Language", property.title());
        assertEquals("Pick one", property.description());
        assertEquals(List.of("en", "fr"), property.enumValues());
        assertEquals(List.of("English", "French"), property.enumTitles());
        assertEquals("en", property.defaultValue());
    }

    @Test
    public void testEnumPropertyOptional() {
        EnumProperty property = new EnumProperty("foo", List.of("A")).optional();
        assertFalse(property.required());
    }

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
        assertNull(property.enumTitles());
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
    public void testEnumPropertyWithTitleAndDescription() {
        EnumProperty property = new EnumProperty("color", List.of("R", "G", "B"))
                .title("Color").description("Pick a color");
        JsonObject json = property.jsonSchema();
        assertEquals("Color", json.getString("title"));
        assertEquals("Pick a color", json.getString("description"));
    }

    @Test
    public void testEnumPropertyWithDefault() {
        EnumProperty property = new EnumProperty("foo", List.of("Red", "Green", "Blue")).defaultValue("Red");
        JsonObject json = property.jsonSchema();
        assertEquals("Red", json.getString("default"));
        assertEquals(3, json.getJsonArray("enum").size());
    }

    @Test
    public void testEnumPropertyDefaultsAbsent() {
        EnumProperty property = new EnumProperty("foo", List.of("A"));
        assertNull(property.title());
        assertNull(property.description());
        assertNull(property.defaultValue());
        JsonObject json = property.jsonSchema();
        assertFalse(json.containsKey("title"));
        assertFalse(json.containsKey("description"));
        assertFalse(json.containsKey("default"));
    }

    @Test
    public void testEnumPropertyEmptyValuesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new EnumProperty("foo", List.of()));
    }

    @Test
    public void testEnumPropertyNullValuesThrows() {
        assertThrows(NullPointerException.class, () -> new EnumProperty("foo", null));
    }

    @Test
    public void testEnumPropertyMismatchedTitlesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new EnumProperty("foo", List.of("a", "b")).enumTitles(List.of("Only One")));
    }

    @Test
    public void testEnumPropertyNullTitlesThrows() {
        assertThrows(NullPointerException.class, () -> new EnumProperty("foo", List.of("a")).enumTitles(null));
    }

    // ==================== MultiStringProperty (multi-select) ====================

    @Test
    public void testMultiStringPropertyAccessors() {
        MultiStringProperty property = new MultiStringProperty("colors", List.of("R", "G", "B"))
                .title("Colors").description("Pick colors").enumTitles(List.of("Red", "Green", "Blue"))
                .minItems(1).maxItems(2).defaultValue(List.of("R"));
        assertEquals("colors", property.name());
        assertTrue(property.required());
        assertEquals("Colors", property.title());
        assertEquals("Pick colors", property.description());
        assertEquals(List.of("R", "G", "B"), property.enumValues());
        assertEquals(List.of("Red", "Green", "Blue"), property.enumTitles());
        assertEquals(1, property.minItems());
        assertEquals(2, property.maxItems());
        assertEquals(List.of("R"), property.defaultValue());
    }

    @Test
    public void testMultiStringPropertyOptional() {
        MultiStringProperty property = new MultiStringProperty("foo", List.of("A")).optional();
        assertFalse(property.required());
    }

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
        assertNull(property.enumTitles());
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
    public void testMultiStringPropertyWithTitleAndDescription() {
        MultiStringProperty property = new MultiStringProperty("tags", List.of("a", "b"))
                .title("Tags").description("Select tags");
        JsonObject json = property.jsonSchema();
        assertEquals("Tags", json.getString("title"));
        assertEquals("Select tags", json.getString("description"));
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

    @Test
    public void testMultiStringPropertyOnlyMinItems() {
        MultiStringProperty property = new MultiStringProperty("foo", List.of("A", "B")).minItems(1);
        JsonObject json = property.jsonSchema();
        assertEquals(1, json.getInt("minItems"));
        assertFalse(json.containsKey("maxItems"));
    }

    @Test
    public void testMultiStringPropertyOnlyMaxItems() {
        MultiStringProperty property = new MultiStringProperty("foo", List.of("A", "B")).maxItems(3);
        JsonObject json = property.jsonSchema();
        assertFalse(json.containsKey("minItems"));
        assertEquals(3, json.getInt("maxItems"));
    }

    @Test
    public void testMultiStringPropertyDefaultsAbsent() {
        MultiStringProperty property = new MultiStringProperty("foo", List.of("A"));
        assertNull(property.title());
        assertNull(property.description());
        assertNull(property.enumTitles());
        assertNull(property.minItems());
        assertNull(property.maxItems());
        assertNull(property.defaultValue());
        JsonObject json = property.jsonSchema();
        assertFalse(json.containsKey("title"));
        assertFalse(json.containsKey("description"));
        assertFalse(json.containsKey("minItems"));
        assertFalse(json.containsKey("maxItems"));
        assertFalse(json.containsKey("default"));
    }

    @Test
    public void testMultiStringPropertyEmptyValuesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MultiStringProperty("foo", List.of()));
    }

    @Test
    public void testMultiStringPropertyNullValuesThrows() {
        assertThrows(NullPointerException.class, () -> new MultiStringProperty("foo", null));
    }

    @Test
    public void testMultiStringPropertyMismatchedTitlesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MultiStringProperty("foo", List.of("a", "b")).enumTitles(List.of("Only One")));
    }

    @Test
    public void testMultiStringPropertyNullTitlesThrows() {
        assertThrows(NullPointerException.class, () -> new MultiStringProperty("foo", List.of("a")).enumTitles(null));
    }
}
