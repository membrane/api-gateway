/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.xml.beautifier;

import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link XMLBeautifier}.
 *
 * These tests verify key interactions with the XMLBeautifierFormatter for
 * common XML inputs using the parse(Reader) overload.
 */
public class XMLBeautifierTest {

    private XMLBeautifierFormatter mockFormatter() {
        // lenient to avoid strict stubbing requirements; we only verify key interactions
        return mock(XMLBeautifierFormatter.class, withSettings().lenient());
    }

    @Test
    @DisplayName("Empty-element: <root/> triggers startTag, writeTag, and closeEmptyTag")
    void emptyElement_isClosedProperly() throws Exception {
        String xml = "<root/>";
        XMLBeautifierFormatter f = mockFormatter();
        XMLBeautifier b = new XMLBeautifier(f);

        b.parse(new StringReader(xml));

        // StAX may yield null version/encoding -> accept nulls
        verify(f, atLeastOnce()).writeVersionAndEncoding(any(), any());

        // Unprefixed elements can have prefix "" or null depending on parser
        verify(f, atLeastOnce()).startTag();
        verify(f, atLeastOnce()).writeTag(any(), eq("root"));
        verify(f, atLeastOnce()).closeEmptyTag();
    }

    @Test
    @DisplayName("Text content: <a>hi</a> writes text and closes tag")
    void textContent_isEmitted() throws Exception {
        String xml = "<a>hi</a>";
        XMLBeautifierFormatter f = mockFormatter();
        XMLBeautifier b = new XMLBeautifier(f);

        b.parse(new StringReader(xml));

        verify(f, atLeastOnce()).writeVersionAndEncoding(nullable(String.class), nullable(String.class));
        verify(f, atLeastOnce()).startTag();
        verify(f, atLeastOnce()).writeTag(nullable(String.class), eq("a"));
        verify(f, atLeastOnce()).writeText("hi");
        verify(f, atLeastOnce()).closeTag(nullable(String.class), eq("a"));
    }

    @Test
    @DisplayName("Namespaces and attributes are written with correct prefixes and values")
    void namespacesAndAttributes_areWritten() throws Exception {
        String xml =
                "<ns:root xmlns:ns=\"urn:x\" ns:id=\"42\" class=\"c\">" +
                "<ns:child/>" +
                "</ns:root>";

        XMLBeautifierFormatter f = mockFormatter();
        XMLBeautifier b = new XMLBeautifier(f);
        b.parse(new StringReader(xml));

        verify(f, atLeastOnce()).writeVersionAndEncoding(nullable(String.class), nullable(String.class));

        // Root start and its tag
        verify(f, atLeastOnce()).startTag();
        verify(f, atLeastOnce()).writeTag(eq("ns"), eq("root"));

        // Namespace + attributes on root
        verify(f, atLeastOnce()).writeNamespaceAttribute(eq("ns"), eq("urn:x"));
        verify(f, atLeastOnce()).writeAttribute(eq("ns"), eq("id"), eq("42"));
        verify(f, atLeastOnce()).writeAttribute(nullable(String.class), eq("class"), eq("c"));

        // Child element (empty)
        verify(f, atLeastOnce()).writeTag(eq("ns"), eq("child"));
        verify(f, atLeastOnce()).closeEmptyTag();

        // Root closed
        verify(f, atLeastOnce()).closeTag(eq("ns"), eq("root"));
    }

    @Test
    @DisplayName("Comment nodes are forwarded to formatter")
    void comments_areWritten() throws Exception {
        String xml = "<a><!-- hello --><b/></a>";
        XMLBeautifierFormatter f = mockFormatter();
        XMLBeautifier b = new XMLBeautifier(f);

        b.parse(new StringReader(xml));

        verify(f, atLeastOnce()).writeVersionAndEncoding( nullable(String.class), nullable(String.class));
        verify(f, atLeastOnce()).writeComment(" hello ");

        verify(f, atLeastOnce()).writeTag(
                nullOrEmptyString(),
                eq("b")
        );

        verify(f, atLeastOnce()).closeEmptyTag();
        verify(f, atLeastOnce()).closeTag(
                nullOrEmptyString(),
                eq("a")
        );
    }

    private static String nullOrEmptyString() {
        return argThat((String p) -> p == null || p.isEmpty());
    }

    @Test
    @DisplayName("Detected encoding is exposed when parsing from InputStream")
    void detectedEncoding_fromInputStream() throws Exception {
        Charset charset = US_ASCII; // use a real charset name
        String xml = """
            <?xml version="1.0" encoding="%s"?>
            <x/>
            """.formatted(charset.displayName());

        XMLBeautifier b = new XMLBeautifier(mockFormatter());
        b.parse(new java.io.ByteArrayInputStream(xml.getBytes(charset)));

        assertEquals(charset.displayName(), b.getDetectedEncoding());
    }

    @Test
    void encodingFromInputStream_isPropagatedToProlog() throws Exception {
        String enc = "UTF-8";
        String xml = """
        <?xml version="1.0" encoding="%s"?>
        <foo/>
        """.formatted(enc);

        // Use bytes in the actual encoding of this file
        byte[] bytes = xml.getBytes(UTF_8);

        StringWriter out = new StringWriter();
        XMLBeautifier beautifier = new XMLBeautifier(new StandardXMLBeautifierFormatter(out, 2));

        beautifier.parse(new ByteArrayInputStream(bytes));
        String result = out.toString();
        assertTrue(result.contains("encoding=\"%s\"".formatted(enc)),
                "Expected prolog to contain encoding attribute, got:\n" + result);

        assertTrue(enc.equalsIgnoreCase(beautifier.getDetectedEncoding()),
                "Expected detected encoding %s but was %s".formatted( enc, beautifier.getDetectedEncoding()));
    }

    @Test
    @DisplayName("CDATA wrappers are normalized: content escaped, no CDATA in output")
    void cdata_isPreservedVerbatim() throws Exception {
        String xml = "<a><![CDATA[1 < 2 & 3]]></a>";

        StringWriter out = new StringWriter();
        XMLBeautifier beautifier = new XMLBeautifier(new StandardXMLBeautifierFormatter(out, 2));

        beautifier.parse(new StringReader(xml));
        String result = out.toString();

        assertTrue(
                result.contains("1 &lt; 2 &amp; 3"),
                "Expected no CDATA sections in output. Got:\n" + result
        );
        assertFalse(result.contains("CDATA"), "Expected no CDATA sections in output");
    }

    @Test
    @DisplayName("Adjacent CDATA sections yield the same character data (foo]]>bar)")
    void adjacentCdata_yieldsSameCharacters() throws Exception {
        // Legal XML that represents "foo]]>bar" via two adjacent CDATA sections
        String xml = "<a><![CDATA[foo]]]]><![CDATA[>bar]]></a>";

        StringWriter out = new StringWriter();
        XMLBeautifier beautifier = new XMLBeautifier(new StandardXMLBeautifierFormatter(out, 2));

        beautifier.parse(new StringReader(xml));
        String result = out.toString();

        assertTrue(
                result.contains("<a>foo]]>bar</a>"),
                "Expected adjacent CDATA sections handling, got:\n" + result
        );
    }

//    @Test
//    @DisplayName("If formatter supports writeCData, it is invoked")
//    void cdata_callsWriteCDataIfSupported() throws Exception {
//        boolean hasWriteCData;
//        try {
//            XMLBeautifierFormatter.class.getMethod("writeCData", String.class);
//            hasWriteCData = true;
//        } catch (NoSuchMethodException e) {
//            hasWriteCData = false;
//        }
//
//        // Skip this test if the formatter doesn't declare writeCData(String)
//        assumeTrue(hasWriteCData, "XMLBeautifierFormatter has no writeCData(String); skipping.");
//
//        XMLBeautifierFormatter f = mockFormatter();
//        XMLBeautifier b = new XMLBeautifier(f);
//
//        String xml = "<a><![CDATA[1 < 2 & 3]]></a>";
//        b.parse(new StringReader(xml));
//
//        // Verify the formatter was asked to write the CDATA content as CDATA, not escaped text
//        verify(f, atLeastOnce()).writeCData("1 < 2 & 3");
//        verify(f, atLeastOnce()).closeTag(nullable(String.class), eq("a"));
//    }
}
