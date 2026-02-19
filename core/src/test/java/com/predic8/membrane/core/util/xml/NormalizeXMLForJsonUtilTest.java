package com.predic8.membrane.core.util.xml;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.xml.NormalizeXMLForJsonUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.emptyList;
import static javax.xml.xpath.XPathConstants.*;
import static org.junit.jupiter.api.Assertions.*;

class NormalizeXMLForJsonUtilTest {

    private static final ObjectMapper om = new ObjectMapper();

    private static Object evaluateXPath(Document doc, String xpath) throws XPathExpressionException {
        return XPathFactory.newInstance()
                .newXPath()
                .evaluate(xpath, doc, NODESET);
    }

    @Nested
    class normalizeForJson_ {

        @Test
        void nullValue() {
            assertNull(normalizeForJson(null));
        }

        @Test
        void passthrough_nonXmlObject() {
            Object o = "foo";
            assertSame(o, normalizeForJson(o));
        }

        @Test
        void nodeList_withTwoElements_becomesList() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a>1</a>
                      <a>2</a>
                    </root>
                    """);

            var norm = normalizeForJson(evaluateXPath(doc, "/root/a"));

            assertTrue(norm instanceof List<?>);
            List<?> list = (List<?>) norm;

            assertEquals(2, list.size());
            assertEquals(1, list.get(0)); // integer
            assertEquals(2, list.get(1)); // integer
        }

        @Test
        void nodeList_withSingleElement_isUnwrapped() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a>1</a>
                    </root>
                    """);

            var norm = normalizeForJson(evaluateXPath(doc, "/root/a"));

            assertInstanceOf(Number.class, norm);
            assertEquals(1, ((Number) norm).intValue());
        }

        @Test
        void singleNode_number_isParsed() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a>1.0</a>
                    </root>
                    """);

            var node = XPathFactory.newInstance()
                    .newXPath()
                    .evaluate("/root/a", doc, NODE);

            var norm = normalizeForJson(node);

            assertInstanceOf(Number.class, norm);
            // numberValue() may yield Double for 1.0
            assertEquals(1.0d, ((Number) norm).doubleValue(), 0.0d);
        }

        @Test
        void singleNode_nonNumber_isTrimmedString() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a>
                        foo
                      </a>
                    </root>
                    """);

            var node = XPathFactory.newInstance()
                    .newXPath()
                    .evaluate("/root/a", doc, NODE);

            assertEquals("foo", normalizeForJson(node));
        }

        @Test
        void nodeList_nonNumber_values_areStrings() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a>foo</a>
                      <a>bar</a>
                    </root>
                    """);

            var norm = normalizeForJson(evaluateXPath(doc, "/root/a"));

            assertInstanceOf(List<?>.class, norm);
            List<?> list = (List<?>) norm;

            assertEquals(List.of("foo", "bar"), list);
        }

        @Test
        void normalizedValue_isJsonSerializable() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a>1</a>
                      <a>2.5</a>
                      <a>foo</a>
                    </root>
                    """);

            var norm = normalizeForJson(evaluateXPath(doc, "/root/a"));

            var roundTrip = om.readValue(om.writeValueAsString(norm), Object.class);
            assertInstanceOf(List<?>.class, roundTrip);
            assertEquals(3, ((List<?>) roundTrip).size());
        }

        @Test
        void emptyNodeList_isJsonSerializable() throws Exception {
            var doc = parseXml("""
                    <root>
                      <a></a>
                    </root>
                    """);
            var norm = normalizeForJson(evaluateXPath(doc, "/root/notThere"));
            assertEquals(emptyList(), norm);
            // verify it survives a Jackson round-trip
            var roundTrip = om.readValue(om.writeValueAsString(norm), Object.class);
            assertEquals(emptyList(), roundTrip);
        }

        private Document parseXml(String xml) throws Exception {
            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        }
    }
}
