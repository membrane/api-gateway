package com.predic8.membrane.core.util.json;

import io.restassured.path.xml.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.json.JsonToXml.XML_PROLOG;
import static com.predic8.membrane.core.util.json.JsonToXml.parseLiteral;
import static org.junit.jupiter.api.Assertions.*;

class JsonToXmlTest {

    XmlPath xp;
    JsonToXml conv;

    @BeforeEach
    void setup() {
        conv = new JsonToXml().rootName("root").arrayName("list").itemName("item");
    }

    @Nested
    class Configuration {

        @Test
        void noRootNameSet() {
            conv.rootName(null);
            XmlPath xp = new XmlPath(conv.toXml("{}"));
            assertEquals("", xp.get("root"));
        }

        @Test
        void topLevelArray_withoutRoot_usesArrayNameAsRoot() {
            conv.rootName(null).arrayName("items");
            assertEquals(2, new XmlPath(conv.toXml("[1,2]")).getList("items.item").size());
        }
    }


    @Nested
    class ObjectsAndArrays {

        @BeforeEach
        void setup() {
            var json = """
                {
                  "one": [1],
                  "nested": [[2]],
                  "three": [[[3]]],
                  "different": [
                    {"foo":4},
                    [1,2,[3,[4]]],
                    5
                  ]
                }
            """;
            xp = new XmlPath(conv.toXml(json));
        }

        @Test
        void one_isPreserved() {
            assertEquals(1, xp.getInt("root.one.list.item"));
            assertEquals(1, xp.getList("root.one.list.item").size());
        }

        @Test
        void nested_isPreserved() {
            assertEquals(1, xp.getList("root.nested.list.item.list.item").size());
            assertEquals(2, xp.getInt("root.nested.list.item.list.item"));
        }

        @Test
        void three_isPreserved() {
            assertEquals(3, xp.getInt("root.three.list.item.list.item.list.item"));
            assertEquals(1, xp.getList("root.three.list.item.list.item.list.item").size());
        }

        @Test
        void different_isPreserved() {
            // {"foo":4}
            assertEquals(4, xp.getInt("root.different.list.item[0].foo"));

            // [1,2,[3,[4]]]
            assertEquals(2, xp.getInt("root.different.list.item[1].list.item[1]"));
            assertEquals(3, xp.getInt("root.different.list.item[1].list.item[2].list.item[0]"));
            assertEquals(4, xp.getInt("root.different.list.item[1].list.item[2].list.item[1].list.item"));

            // 5
            assertEquals(5, xp.getInt("root.different.list.item[2]"));
        }
    }

    @Nested
    class Objects {

        @Test
        void singleProperty() {
            conv.rootName(null);

            String xml = conv.toXml("""
                {
                    "person": {
                        "name": "John",
                        "age": 30
                    }
                }
                """);
            XmlPath xp = new XmlPath(xml);
            assertEquals("John", xp.getString("person.name"));
            assertEquals("30",  xp.getString("person.age"));
        }

    }

    @Nested
    class Arrays {

        @Test
        void emptyArray_isConverted() {
            String xml = conv.toXml("[]");
            XmlPath xp = new XmlPath(xml);

            assertNotNull(xp.get("root.list"));
            assertEquals("", xp.get("root.list"));
            assertTrue(xml.contains("<root><list></list></root>"));
        }

        @Test
        void singleElementArray_isConverted() {
            var xml = conv.toXml("[1]");
            XmlPath xp = new XmlPath(xml);

            assertEquals(1, xp.getInt("root.list.item[0]"));
            assertEquals(1, xp.getList("root.list.item").size());
        }

        @Test
        void flatArray_isConverted() {
            XmlPath xp = new XmlPath(conv.toXml("[1,2,3]"));

            assertEquals(3, xp.getList("root.list.item").size());
            assertEquals(1, xp.getInt("root.list.item[0]"));
            assertEquals(2, xp.getInt("root.list.item[1]"));
            assertEquals(3, xp.getInt("root.list.item[2]"));
        }

        @Test
        void nestedArray_isConverted() {
            XmlPath xp = new XmlPath(conv.toXml("[1,[2],[[3]]]"));

            assertEquals(1, xp.getInt("root.list.item[0]"));
            assertEquals(2, xp.getInt("root.list.item[1].list.item[0]"));
            assertEquals(3, xp.getInt("root.list.item[2].list.item[0].list.item[0]"));
        }
    }

    @Nested
    class PrimitiveTypes {

        @Test
        void stringValue_isConverted() {
            assertEquals("hello", new XmlPath(conv.toXml("\"hello\"")).getString("root"));
        }

        @Test
        void numberValue_isConverted() {
            assertEquals(123, new XmlPath(conv.toXml("123")).getInt("root"));
        }

        @Test
        void booleanValue_isConverted() {
            assertTrue(new XmlPath(conv.toXml("true")).getBoolean("root"));
        }

        @Test
        void nullValue_isConvertedToEmptyNode() {
            XmlPath xp = new XmlPath(conv.toXml("null"));
            String v = xp.getString("root");
            assertTrue(v == null || v.isBlank());
        }
    }

    @Test
    void escape() {
        assertTrue( conv.toXml("""
                { "chars": "> < & \\" '" }
                """).contains("<root><chars>&gt; &lt; &amp; \" '</chars></root>"));
    }

    @Test
    void strangeKeys() {
        conv.rootName(null);
        assertEquals( XML_PROLOG + "<a_b>1</a_b>",conv.toXml("""
                { "a b": 1 }
                """));
        assertEquals( XML_PROLOG + "<_123>1</_123>",conv.toXml("""
                { "123": 1 }
                """));
    }

    @Test
    void testParseLiteral() {
        // Integer
        assertEquals(42L, parseLiteral("42"));
        assertEquals(-7L, parseLiteral("-7"));

        // Float / Double
        assertEquals(3.14d, parseLiteral("3.14"));
        assertEquals(-0.1d, parseLiteral("-0.1"));
        assertEquals(1.2e3d, parseLiteral("1.2e3"));
        assertEquals(-5.6E-2d, parseLiteral("-5.6E-2"));

        // Overflow case → stays Double
        Object big = parseLiteral("999999999999999999999999");
        assertInstanceOf(Double.class, big);

        // Quoted strings
        assertEquals("hello", parseLiteral("\"hello\""));
        assertEquals("", parseLiteral("\"\""));

        // Unquoted strings
        assertEquals("abc", parseLiteral("abc"));
        assertEquals("123abc", parseLiteral("123abc"));
        assertEquals("true", parseLiteral("true")); // not converted to boolean

        // Edge cases
        assertEquals("-", parseLiteral("-"));     // not a number
        assertEquals(".", parseLiteral("."));     // not a number
    }
}
