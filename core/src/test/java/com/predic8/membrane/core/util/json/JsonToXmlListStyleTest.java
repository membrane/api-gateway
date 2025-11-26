package com.predic8.membrane.core.util.json;

import io.restassured.path.xml.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonToXmlListStyleTest {

    XmlPath xp;
    JsonToXmlListStyle conv;

    @BeforeEach
    void setup() {
        conv = new JsonToXmlListStyle();
        conv.setRootName("root");
        conv.setArrayName("list");
        conv.setItemName("item");
    }

    @Nested
    class Configuration {

        @Test
        void noRoot() {
            conv.setRootName(null);
            String xml = conv.toXml("{}");
            System.out.println(xml);
            XmlPath xpLocal = new XmlPath(xml);
            assertEquals("", xpLocal.get("root"));
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
            conv.setRootName(null);
            String xml = conv.toXml("""
                    {
                        "person": {
                            "name": "John",
                            "age": 30
                        }
                    }
                    """);
            System.out.println(xml);
        }

    }

    @Nested
    class Arrays {

        @Test
        void emptyArray_isConverted() {
            String xml = conv.toXml("[]");
            XmlPath xpLocal = new XmlPath(xml);

            assertNotNull(xpLocal.get("root.list"));
            assertEquals("", xpLocal.get("root.list"));
            assertEquals("<root><list></list></root>",xml);
        }

        @Test
        void singleElementArray_isConverted() {
            var xml = conv.toXml("[1]");
            XmlPath xpLocal = new XmlPath(xml);

            assertEquals(1, xpLocal.getInt("root.list.item[0]"));
            assertEquals(1, xpLocal.getList("root.list.item").size());
        }

        @Test
        void flatArray_isConverted() {
            XmlPath xpLocal = new XmlPath(conv.toXml("[1,2,3]"));

            assertEquals(3, xpLocal.getList("root.list.item").size());
            assertEquals(1, xpLocal.getInt("root.list.item[0]"));
            assertEquals(2, xpLocal.getInt("root.list.item[1]"));
            assertEquals(3, xpLocal.getInt("root.list.item[2]"));
        }

        @Test
        void nestedArray_isConverted() {
            XmlPath xpLocal = new XmlPath(conv.toXml("[1,[2],[[3]]]"));

            assertEquals(1, xpLocal.getInt("root.list.item[0]"));
            assertEquals(2, xpLocal.getInt("root.list.item[1].list.item[0]"));
            assertEquals(3, xpLocal.getInt("root.list.item[2].list.item[0].list.item[0]"));
        }
    }

    @Nested
    class PrimitiveTypes {

        @Test
        void stringValue_isConverted() {
            XmlPath xpLocal = new XmlPath(conv.toXml("\"hello\""));
            assertEquals("hello", xpLocal.getString("root"));
        }

        @Test
        void numberValue_isConverted() {
            XmlPath xpLocal = new XmlPath(conv.toXml("123"));
            assertEquals(123, xpLocal.getInt("root"));
        }

        @Test
        void booleanValue_isConverted() {
            XmlPath xpLocal = new XmlPath(conv.toXml("true"));
            assertTrue(xpLocal.getBoolean("root"));
        }

        @Test
        void nullValue_isConvertedToEmptyNode() {
            XmlPath xpLocal = new XmlPath(conv.toXml("null"));
            String v = xpLocal.getString("root");
            assertTrue(v == null || v.isBlank());
        }
    }
}
