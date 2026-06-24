/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.api.elicitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.mcp.api.elicitation.Elicitation.Response.Action.ACCEPT;
import static org.wildfly.mcp.api.elicitation.Elicitation.Response.Action.CANCEL;
import static org.wildfly.mcp.api.elicitation.Elicitation.Response.Action.DECLINE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ElicitationResponseTestCase {

    // ==================== Constructor & action ====================

    @Test
    public void testNullActionThrows() {
        assertThrows(NullPointerException.class, () -> new Elicitation.Response(null, Map.of()));
    }

    @Test
    public void testNullContentNormalisedToEmptyMap() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, null);
        assertTrue(response.content().isEmpty());
    }

    @Test
    public void testContentIsUnmodifiable() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("key", "value");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, mutable);
        assertThrows(UnsupportedOperationException.class, () -> response.content().put("new", "val"));
    }

    // ==================== isAccepted / isDeclined / isCancelled ====================

    @Test
    public void testIsAccepted() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertTrue(response.isAccepted());
        assertFalse(response.isDeclined());
        assertFalse(response.isCancelled());
    }

    @Test
    public void testIsDeclined() {
        Elicitation.Response response = new Elicitation.Response(DECLINE, Map.of());
        assertFalse(response.isAccepted());
        assertTrue(response.isDeclined());
        assertFalse(response.isCancelled());
    }

    @Test
    public void testIsCancelled() {
        Elicitation.Response response = new Elicitation.Response(CANCEL, Map.of());
        assertFalse(response.isAccepted());
        assertFalse(response.isDeclined());
        assertTrue(response.isCancelled());
    }

    // ==================== getString(String) ====================

    @Test
    public void testGetStringByKeyPresent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("name", "Alice"));
        assertEquals(Optional.of("Alice"), response.getString("name"));
    }

    @Test
    public void testGetStringByKeyAbsent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getString("name").isPresent());
    }

    @Test
    public void testGetStringByKeyWrongType() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("name", 42));
        assertFalse(response.getString("name").isPresent());
    }

    // ==================== getString(StringProperty) ====================

    @Test
    public void testGetStringByPropertyPresent() {
        StringProperty prop = new StringProperty("name");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("name", "Alice"));
        assertEquals(Optional.of("Alice"), response.getString(prop));
    }

    @Test
    public void testGetStringByPropertyAbsentNoDefault() {
        StringProperty prop = new StringProperty("name");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getString(prop).isPresent());
    }

    @Test
    public void testGetStringByPropertyAbsentWithDefault() {
        StringProperty prop = new StringProperty("name").defaultValue("Bob");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertEquals(Optional.of("Bob"), response.getString(prop));
    }

    @Test
    public void testGetStringByPropertyValueOverridesDefault() {
        StringProperty prop = new StringProperty("name").defaultValue("Bob");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("name", "Alice"));
        assertEquals(Optional.of("Alice"), response.getString(prop));
    }

    @Test
    public void testGetStringByPropertyWrongType() {
        StringProperty prop = new StringProperty("name").defaultValue("Bob");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("name", 42));
        assertFalse(response.getString(prop).isPresent());
    }

    // ==================== getString(EnumProperty) ====================

    @Test
    public void testGetStringByEnumPropertyPresent() {
        EnumProperty prop = new EnumProperty("color", List.of("Red", "Green", "Blue"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("color", "Red"));
        assertEquals(Optional.of("Red"), response.getString(prop));
    }

    @Test
    public void testGetStringByEnumPropertyAbsentNoDefault() {
        EnumProperty prop = new EnumProperty("color", List.of("Red", "Green"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getString(prop).isPresent());
    }

    @Test
    public void testGetStringByEnumPropertyAbsentWithDefault() {
        EnumProperty prop = new EnumProperty("color", List.of("Red", "Green")).defaultValue("Red");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertEquals(Optional.of("Red"), response.getString(prop));
    }

    @Test
    public void testGetStringByEnumPropertyValueOverridesDefault() {
        EnumProperty prop = new EnumProperty("color", List.of("Red", "Green")).defaultValue("Red");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("color", "Green"));
        assertEquals(Optional.of("Green"), response.getString(prop));
    }

    @Test
    public void testGetStringByEnumPropertyWrongType() {
        EnumProperty prop = new EnumProperty("color", List.of("Red", "Green")).defaultValue("Red");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("color", 123));
        assertFalse(response.getString(prop).isPresent());
    }

    // ==================== getBoolean(String) ====================

    @Test
    public void testGetBooleanByKeyPresent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("flag", true));
        assertEquals(Optional.of(true), response.getBoolean("flag"));
    }

    @Test
    public void testGetBooleanByKeyAbsent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getBoolean("flag").isPresent());
    }

    @Test
    public void testGetBooleanByKeyWrongType() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("flag", "true"));
        assertFalse(response.getBoolean("flag").isPresent());
    }

    // ==================== getBoolean(BooleanProperty) ====================

    @Test
    public void testGetBooleanByPropertyPresent() {
        BooleanProperty prop = new BooleanProperty("flag");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("flag", false));
        assertEquals(Optional.of(false), response.getBoolean(prop));
    }

    @Test
    public void testGetBooleanByPropertyAbsentNoDefault() {
        BooleanProperty prop = new BooleanProperty("flag");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getBoolean(prop).isPresent());
    }

    @Test
    public void testGetBooleanByPropertyAbsentWithDefault() {
        BooleanProperty prop = new BooleanProperty("flag").defaultValue(true);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertEquals(Optional.of(true), response.getBoolean(prop));
    }

    @Test
    public void testGetBooleanByPropertyValueOverridesDefault() {
        BooleanProperty prop = new BooleanProperty("flag").defaultValue(true);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("flag", false));
        assertEquals(Optional.of(false), response.getBoolean(prop));
    }

    @Test
    public void testGetBooleanByPropertyWrongType() {
        BooleanProperty prop = new BooleanProperty("flag").defaultValue(true);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("flag", "true"));
        assertFalse(response.getBoolean(prop).isPresent());
    }

    // ==================== getInteger(String) ====================

    @Test
    public void testGetIntegerByKeyPresent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("count", 42));
        assertEquals(Optional.of(42), response.getInteger("count"));
    }

    @Test
    public void testGetIntegerByKeyAbsent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getInteger("count").isPresent());
    }

    @Test
    public void testGetIntegerByKeyWrongType() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("count", "42"));
        assertFalse(response.getInteger("count").isPresent());
    }

    @Test
    public void testGetIntegerByKeyRejectsDouble() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("count", 3.7));
        assertFalse(response.getInteger("count").isPresent());
    }

    // ==================== getInteger(IntegerProperty) ====================

    @Test
    public void testGetIntegerByPropertyPresent() {
        IntegerProperty prop = new IntegerProperty("count");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("count", 10));
        assertEquals(Optional.of(10), response.getInteger(prop));
    }

    @Test
    public void testGetIntegerByPropertyAbsentNoDefault() {
        IntegerProperty prop = new IntegerProperty("count");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getInteger(prop).isPresent());
    }

    @Test
    public void testGetIntegerByPropertyAbsentWithDefault() {
        IntegerProperty prop = new IntegerProperty("count").defaultValue(5);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertEquals(Optional.of(5), response.getInteger(prop));
    }

    @Test
    public void testGetIntegerByPropertyValueOverridesDefault() {
        IntegerProperty prop = new IntegerProperty("count").defaultValue(5);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("count", 99));
        assertEquals(Optional.of(99), response.getInteger(prop));
    }

    @Test
    public void testGetIntegerByPropertyWrongType() {
        IntegerProperty prop = new IntegerProperty("count").defaultValue(5);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("count", "ten"));
        assertFalse(response.getInteger(prop).isPresent());
    }

    // ==================== getNumber(String) ====================

    @Test
    public void testGetNumberByKeyPresent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("rate", 3.14));
        assertEquals(Optional.of(3.14), response.getNumber("rate"));
    }

    @Test
    public void testGetNumberByKeyAbsent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getNumber("rate").isPresent());
    }

    @Test
    public void testGetNumberByKeyWrongType() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("rate", "3.14"));
        assertFalse(response.getNumber("rate").isPresent());
    }

    @Test
    public void testGetNumberByKeyCoercesInteger() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("rate", 7));
        assertEquals(Optional.of(7.0), response.getNumber("rate"));
    }

    // ==================== getNumber(NumberProperty) ====================

    @Test
    public void testGetNumberByPropertyPresent() {
        NumberProperty prop = new NumberProperty("rate");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("rate", 2.5));
        assertEquals(Optional.of(2.5), response.getNumber(prop));
    }

    @Test
    public void testGetNumberByPropertyAbsentNoDefault() {
        NumberProperty prop = new NumberProperty("rate");
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getNumber(prop).isPresent());
    }

    @Test
    public void testGetNumberByPropertyAbsentWithDefault() {
        NumberProperty prop = new NumberProperty("rate").defaultValue(1.0);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertEquals(Optional.of(1.0), response.getNumber(prop));
    }

    @Test
    public void testGetNumberByPropertyValueOverridesDefault() {
        NumberProperty prop = new NumberProperty("rate").defaultValue(1.0);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("rate", 9.9));
        assertEquals(Optional.of(9.9), response.getNumber(prop));
    }

    @Test
    public void testGetNumberByPropertyWrongType() {
        NumberProperty prop = new NumberProperty("rate").defaultValue(1.0);
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("rate", "fast"));
        assertFalse(response.getNumber(prop).isPresent());
    }

    // ==================== getStrings(String) ====================

    @Test
    public void testGetStringsByKeyPresent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("tags", List.of("a", "b")));
        assertEquals(Optional.of(List.of("a", "b")), response.getStrings("tags"));
    }

    @Test
    public void testGetStringsByKeyAbsent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getStrings("tags").isPresent());
    }

    @Test
    public void testGetStringsByKeyWrongType() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("tags", "not-a-list"));
        assertFalse(response.getStrings("tags").isPresent());
    }

    @Test
    public void testGetStringsByKeyNonStringElements() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("tags", List.of(1, 2, 3)));
        assertFalse(response.getStrings("tags").isPresent());
    }

    @Test
    public void testGetStringsByKeyMixedElements() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("tags", List.of("a", 2)));
        assertFalse(response.getStrings("tags").isPresent());
    }

    // ==================== getStrings(MultiStringProperty) ====================

    @Test
    public void testGetStringsByPropertyPresent() {
        MultiStringProperty prop = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("colors", List.of("Red", "Blue")));
        assertEquals(Optional.of(List.of("Red", "Blue")), response.getStrings(prop));
    }

    @Test
    public void testGetStringsByPropertyAbsentNoDefault() {
        MultiStringProperty prop = new MultiStringProperty("colors", List.of("Red", "Green"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertFalse(response.getStrings(prop).isPresent());
    }

    @Test
    public void testGetStringsByPropertyAbsentWithDefault() {
        MultiStringProperty prop = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"))
                .defaultValue(List.of("Red"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of());
        assertEquals(Optional.of(List.of("Red")), response.getStrings(prop));
    }

    @Test
    public void testGetStringsByPropertyValueOverridesDefault() {
        MultiStringProperty prop = new MultiStringProperty("colors", List.of("Red", "Green", "Blue"))
                .defaultValue(List.of("Red"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("colors", List.of("Green", "Blue")));
        assertEquals(Optional.of(List.of("Green", "Blue")), response.getStrings(prop));
    }

    @Test
    public void testGetStringsByPropertyNonStringElements() {
        MultiStringProperty prop = new MultiStringProperty("colors", List.of("Red", "Green"))
                .defaultValue(List.of("Red"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("colors", List.of(1, 2)));
        assertFalse(response.getStrings(prop).isPresent());
    }

    @Test
    public void testGetStringsByPropertyWrongType() {
        MultiStringProperty prop = new MultiStringProperty("colors", List.of("Red", "Green"));
        Elicitation.Response response = new Elicitation.Response(ACCEPT, Map.of("colors", "not-a-list"));
        assertFalse(response.getStrings(prop).isPresent());
    }

    // ==================== null content ====================

    @Test
    public void testAllGettersWithNullContent() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, null);

        assertFalse(response.getString("k").isPresent());
        assertFalse(response.getBoolean("k").isPresent());
        assertFalse(response.getInteger("k").isPresent());
        assertFalse(response.getNumber("k").isPresent());
        assertFalse(response.getStrings("k").isPresent());

        assertFalse(response.getString(new StringProperty("k")).isPresent());
        assertFalse(response.getString(new EnumProperty("k", List.of("A"))).isPresent());
        assertFalse(response.getBoolean(new BooleanProperty("k")).isPresent());
        assertFalse(response.getInteger(new IntegerProperty("k")).isPresent());
        assertFalse(response.getNumber(new NumberProperty("k")).isPresent());
        assertFalse(response.getStrings(new MultiStringProperty("k", List.of("A"))).isPresent());
    }

    @Test
    public void testPropertyGettersWithNullContentFallBackToDefaults() {
        Elicitation.Response response = new Elicitation.Response(ACCEPT, null);

        assertEquals(Optional.of("def"), response.getString(new StringProperty("k").defaultValue("def")));
        assertEquals(Optional.of("A"), response.getString(new EnumProperty("k", List.of("A", "B")).defaultValue("A")));
        assertEquals(Optional.of(true), response.getBoolean(new BooleanProperty("k").defaultValue(true)));
        assertEquals(Optional.of(7), response.getInteger(new IntegerProperty("k").defaultValue(7)));
        assertEquals(Optional.of(3.14), response.getNumber(new NumberProperty("k").defaultValue(3.14)));
        assertEquals(Optional.of(List.of("A")), response.getStrings(new MultiStringProperty("k", List.of("A", "B")).defaultValue(List.of("A"))));
    }
}
