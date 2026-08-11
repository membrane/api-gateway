/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.xml.*;
import com.predic8.membrane.core.util.xml.parser.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.namespace.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class XMLUtilTest {

    static final QName JA = new QName("a","a");
    static final groovy.namespace.QName GA = new groovy.namespace.QName("a","a");

    static final QName PREFIX_A = new QName("ns","local","a");
    static final QName PREFIX_B = new QName("ns","local","b");

    @Test
    void groovyToJavaxQName() {
        assertEquals(JA, XMLUtil.groovyToJavaxQName(GA));
    }

    @Test
    void equalsIgnorePrefix() {
        assertEquals(PREFIX_A, PREFIX_B);
    }

    /**
     * The message's charset (here ISO-8859-1) must govern decoding, not the JVM default
     * charset, so a non-ASCII byte in the body is decoded correctly.
     */
    @Test
    void getInputSourceHonorsTheMessagesCharset() throws Exception {
        Request req = new Request();
        req.setBodyContent("""
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <order><city>Bönnigheim</city></order>""".getBytes(ISO_8859_1));
        req.getHeader().setContentType("text/xml; charset=ISO-8859-1");

        Document doc = HardenedXmlParser.getInstance().parse(XMLUtil.getInputSource(req));

        assertEquals("Bönnigheim", doc.getElementsByTagName("city").item(0).getTextContent());
    }

    /**
     * With no charset on the header, the XML declaration (read from the byte stream, not
     * pre-decoded) still decides.
     */
    @Test
    void getInputSourceFallsBackToTheXmlDeclarationWithoutAHeaderCharset() throws Exception {
        Request req = new Request();
        req.setBodyContent("""
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <order><city>Bönnigheim</city></order>""".getBytes(ISO_8859_1));
        req.getHeader().setContentType("text/xml");

        Document doc = HardenedXmlParser.getInstance().parse(XMLUtil.getInputSource(req));

        assertEquals("Bönnigheim", doc.getElementsByTagName("city").item(0).getTextContent());
    }
}