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

    public static final String MY_SPECIAL_ENCODING = "My-Special-Encoding";

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

        assertTrue(result.contains("encoding = \"%s\"".formatted(enc)),
                "Expected prolog to contain encoding attribute, got:\n" + result);

        assertTrue(enc.equalsIgnoreCase(beautifier.getDetectedEncoding()),
                "Expected detected encoding %s but was %s".formatted( enc, beautifier.getDetectedEncoding()));
    }
}
