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

package com.predic8.membrane.core.http.xml;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.StringWriter;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.http.Response.ok;
import static org.junit.jupiter.api.Assertions.*;

class XMLBodyTest {

    // Attributes carry a namespace in these tests: XMLBody mistakes Woodstox's "" for a namespace URI

    @Test
    void copiesTheBody() throws Exception {
        final var written = write("<foo xmlns:p='urn:x' p:a='1'><bar>text</bar></foo>");
        assertTrue(written.contains("p:a=\"1\"") && written.contains("<bar>text</bar>"), written);
    }

    @Test
    void entityDeclaredInDoctypeIsNotExpanded() throws Exception {
        // The JDK parser rejects the undeclared reference, Woodstox drops it - neither may expand it
        final String written;
        try {
            written = write("<!DOCTYPE foo [<!ENTITY e 'xyz'>]><foo xmlns:p='urn:x' p:a='&e;&e;'/>");
        } catch (XMLStreamException e) {
            assertTrue(e.getMessage().contains("\"e\""), e.getMessage());
            return;
        }
        assertFalse(written.contains("xyz"), written);
    }

    private static String write(String body) throws XMLStreamException {
        final var out = new StringWriter();
        // The JDK's writer, as at runtime: Woodstox reaches the test classpath through CXF
        final var writer = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out);
        writer.writeStartElement("body");
        new XMLBody(ok().contentType(TEXT_XML_UTF8).body(body).build()).write(writer);
        writer.writeEndElement();
        writer.flush();
        return out.toString();
    }
}
