/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import com.predic8.membrane.core.HttpRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XSLTTransformerTest {

    /**
     * GHSA-23v5-9h8v-7282: every caller (transform, soap2Rest, rest2Soap) goes through
     * {@link XSLTTransformer#transform}, so the message must be parsed without DOCTYPE support.
     */
    @Test
    void doctypeInMessageIsRejected(@TempDir Path dir) throws Exception {
        final var secret = dir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-1234");

        final var transformer = new XSLTTransformer(null, new HttpRouter(), 1);
        final var xml = """
                <?xml version="1.0"?>
                <!DOCTYPE a [<!ENTITY xxe SYSTEM "%s">]>
                <a>&xxe;</a>
                """.formatted(secret.toUri());

        assertThrows(TransformerException.class, () -> transformer.transform(new StreamSource(new StringReader(xml))));
    }

    @Test
    void messageWithoutDoctypeIsTransformed() throws Exception {
        final var transformer = new XSLTTransformer(null, new HttpRouter(), 1);
        final var result = new String(transformer.transform(new StreamSource(new StringReader("<a>b</a>"))), UTF_8);
        assertTrue(result.contains("<a>b</a>"), result);
    }

    @Test
    void stylesheetCanStillIncludeOtherStylesheets(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("included.xsl"), """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/a"><included><xsl:value-of select="."/></included></xsl:template>
                </xsl:stylesheet>
                """);
        final var main = dir.resolve("main.xsl");
        Files.writeString(main, """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:include href="included.xsl"/>
                </xsl:stylesheet>
                """);

        final var transformer = new XSLTTransformer(main.toString(), new HttpRouter(), 1);
        final var result = new String(transformer.transform(new StreamSource(new StringReader("<a>b</a>"))), UTF_8);
        assertTrue(result.contains("<included>b</included>"), result);
    }

    /**
     * GHSA-23v5-9h8v-7282: a stylesheet that passes a URL from the message to document() must not
     * let the loaded file pull in external entities.
     */
    @Test
    void documentFunctionDoesNotResolveExternalEntities(@TempDir Path dir) throws Exception {
        final var secret = dir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-1234");
        final var external = dir.resolve("external.xml");
        Files.writeString(external, """
                <!DOCTYPE r [<!ENTITY xxe SYSTEM "%s">]>
                <r>&xxe;</r>
                """.formatted(secret.toUri()));

        final var transformer = new XSLTTransformer(writeDocumentStylesheet(dir).toString(), new HttpRouter(), 1);
        final var e = assertThrows(TransformerException.class, () -> transformDocumentReference(transformer, external));
        assertTrue(e.getMessage().contains("accessExternalDTD"), e.getMessage());
    }

    @Test
    void documentFunctionStillLoadsPlainDocuments(@TempDir Path dir) throws Exception {
        final var plain = dir.resolve("plain.xml");
        Files.writeString(plain, "<r>hello</r>");

        final var transformer = new XSLTTransformer(writeDocumentStylesheet(dir).toString(), new HttpRouter(), 1);
        assertTrue(transformDocumentReference(transformer, plain).contains("<out>hello</out>"));
    }

    @Test
    void stylesheetCanStillDeclareInternalEntities(@TempDir Path dir) throws Exception {
        final var stylesheet = dir.resolve("entities.xsl");
        Files.writeString(stylesheet, """
                <!DOCTYPE xsl:stylesheet [<!ENTITY greeting "hello">]>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><out>&greeting;</out></xsl:template>
                </xsl:stylesheet>
                """);

        final var transformer = new XSLTTransformer(stylesheet.toString(), new HttpRouter(), 1);
        final var result = new String(transformer.transform(new StreamSource(new StringReader("<a/>"))), UTF_8);
        assertTrue(result.contains("<out>hello</out>"), result);
    }

    private static Path writeDocumentStylesheet(Path dir) throws Exception {
        final var stylesheet = dir.resolve("document.xsl");
        Files.writeString(stylesheet, """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/a"><out><xsl:value-of select="document(string(@href))"/></out></xsl:template>
                </xsl:stylesheet>
                """);
        return stylesheet;
    }

    private static String transformDocumentReference(XSLTTransformer transformer, Path document) throws Exception {
        final var message = "<a href=\"%s\"/>".formatted(document.toUri());
        return new String(transformer.transform(new StreamSource(new StringReader(message))), UTF_8);
    }
}
