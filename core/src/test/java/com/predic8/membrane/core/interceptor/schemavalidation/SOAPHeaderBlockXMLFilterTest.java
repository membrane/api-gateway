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

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.util.SOAPUtil;
import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class SOAPHeaderBlockXMLFilterTest {

    private static final String SOAP11 = """
            <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
              <s11:Header>
                <a:First xmlns:a="https://example.com/a"><a:value>1</a:value></a:First>
                <b:Second xmlns:b="https://example.com/b" s11:mustUnderstand="1" s11:actor="http://schemas.xmlsoap.org/soap/actor/next" id="x">
                  <b:value>2</b:value>
                </b:Second>
              </s11:Header>
              <s11:Body><c:body xmlns:c="https://example.com/c">3</c:body></s11:Body>
            </s11:Envelope>
            """;

    @Test
    void emitsOnlyTheSelectedBlock() throws Exception {
        var out = filterHeaderBlock(SOAP11, 0);

        assertTrue(out.contains("First"), out);
        assertTrue(out.contains(">1<"), out);
        assertFalse(out.contains("Second"), out);
        assertFalse(out.contains("Envelope"), out);
        assertFalse(out.contains("Header"), out);
        assertFalse(out.contains("body"), out);
    }

    @Test
    void emitsLaterBlockWithoutSoapAttributes() throws Exception {
        var out = filterHeaderBlock(SOAP11, 1);

        assertTrue(out.contains("Second"), out);
        assertTrue(out.contains(">2<"), out);
        assertTrue(out.contains("id=\"x\""), "attributes of the block itself are kept: " + out);
        assertFalse(out.contains("mustUnderstand"), out);
        assertFalse(out.contains("actor"), out);
        assertFalse(out.contains("First"), out);
    }

    @Test
    void soap12() throws Exception {
        var out = filterHeaderBlock("""
                <s12:Envelope xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                  <s12:Header>
                    <a:First xmlns:a="https://example.com/a" s12:mustUnderstand="true" s12:role="urn:r" s12:relay="true">1</a:First>
                  </s12:Header>
                  <s12:Body/>
                </s12:Envelope>
                """, 0);

        assertTrue(out.contains("First"), out);
        assertFalse(out.contains("mustUnderstand"), out);
        assertFalse(out.contains("role"), out);
        assertFalse(out.contains("relay"), out);
    }

    /**
     * A block's descendants may well be named {@code Header} themselves; only the direct child of
     * the envelope is the SOAP header.
     */
    @Test
    void nestedHeaderElementInBlock() throws Exception {
        var out = filterHeaderBlock("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header>
                    <a:First xmlns:a="https://example.com/a"><s11:Header>inner</s11:Header></a:First>
                    <a:Second xmlns:a="https://example.com/a"/>
                  </s11:Header>
                  <s11:Body/>
                </s11:Envelope>
                """, 1);

        assertTrue(out.contains("Second"), out);
        assertFalse(out.contains("inner"), out);
    }

    private static String filterHeaderBlock(String soap, int index) throws Exception {
        var tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        var sw = new StringWriter();
        t.transform(SOAPUtil.getSOAPHeaderBlock(new ByteArrayInputStream(soap.getBytes(UTF_8)), index), new StreamResult(sw));
        return sw.toString();
    }
}
