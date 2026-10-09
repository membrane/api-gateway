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

import com.predic8.membrane.core.router.DummyTestRouter;
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

        final var transformer = new XSLTTransformer(null, new DummyTestRouter(), null, 1);
        final var xml = """
                <?xml version="1.0"?>
                <!DOCTYPE a [<!ENTITY xxe SYSTEM "%s">]>
                <a>&xxe;</a>
                """.formatted(secret.toUri());

        assertThrows(TransformerException.class, () -> transformer.transform(new StreamSource(new StringReader(xml))));
    }

    @Test
    void messageWithoutDoctypeIsTransformed() throws Exception {
        final var transformer = new XSLTTransformer(null, new DummyTestRouter(), null, 1);
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

        final var transformer = new XSLTTransformer(main.toString(), new DummyTestRouter(), null, 1);
        final var result = new String(transformer.transform(new StreamSource(new StringReader("<a>b</a>"))), UTF_8);
        assertTrue(result.contains("<included>b</included>"), result);
    }
}
