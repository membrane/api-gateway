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

import com.predic8.membrane.annot.Constants.SoapVersion;
import com.predic8.membrane.core.util.SOAPUtil;
import org.junit.jupiter.api.Test;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP11;
import static com.predic8.membrane.annot.Constants.SoapVersion.SOAP12;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultDetailXMLFilterTest {

    /**
     * The fault payload may itself contain an element named {@code detail} — fault schemas
     * usually leave {@code elementFormDefault} at its {@code unqualified} default. Matching the
     * name again on the closing tag would end the payload there, dropping everything after it
     * and emitting an unbalanced document.
     */
    @Test
    void keepsNestedDetailElement() throws Exception {
        String out = filterFaultDetail(SOAP11, """
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body>
                    <s11:Fault>
                      <faultcode>Server</faultcode>
                      <faultstring>City not found</faultstring>
                      <detail>
                        <p:err xmlns:p="http://example.com/svc">
                          <detail>inner</detail>
                          <code>7</code>
                        </p:err>
                      </detail>
                    </s11:Fault>
                  </s11:Body>
                </s11:Envelope>
                """);

        assertTrue(out.contains("err"), out);
        assertTrue(out.contains("inner"), out);     // the nested detail survived
        assertTrue(out.contains("code"), out);      // ... and so did its sibling
        assertTrue(out.contains("7"), out);
    }

    /**
     * Only the detail content may be emitted. Text from the surrounding envelope would land in
     * front of the payload's root element, making the result unusable as an XML document.
     */
    @Test
    void stripsEnvelopeText() throws Exception {
        String out = filterFaultDetail(SOAP11, """
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body>
                    <s11:Fault>
                      <faultcode>Server</faultcode>
                      <faultstring>City not found</faultstring>
                      <detail><p:err xmlns:p="http://example.com/svc">boom</p:err></detail>
                    </s11:Fault>
                  </s11:Body>
                </s11:Envelope>
                """);

        assertTrue(out.contains("err"), out);
        assertTrue(out.contains("boom"), out);
        assertFalse(out.contains("City not found"), out);
        assertFalse(out.contains("Server"), out);
    }

    @Test
    void soap12Detail() throws Exception {
        String out = filterFaultDetail(SOAP12, """
                <s12:Envelope xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                  <s12:Body>
                    <s12:Fault>
                      <s12:Code><s12:Value>Receiver</s12:Value></s12:Code>
                      <s12:Reason><s12:Text>City not found</s12:Text></s12:Reason>
                      <s12:Detail><p:err xmlns:p="http://example.com/svc">boom</p:err></s12:Detail>
                    </s12:Fault>
                  </s12:Body>
                </s12:Envelope>
                """);

        assertTrue(out.contains("err"), out);
        assertTrue(out.contains("boom"), out);
        assertFalse(out.contains("Receiver"), out);
        assertFalse(out.contains("City not found"), out);
    }

    /**
     * The boundary is matched per SOAP version: an unqualified SOAP 1.1 {@code detail} is not a
     * SOAP 1.2 {@code Detail}, so nothing is extracted.
     */
    @Test
    void soap11DetailNotMatchedInSoap12Mode() throws Exception {
        String out = filterFaultDetail(SOAP12, """
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body>
                    <s11:Fault>
                      <faultcode>Server</faultcode>
                      <detail><p:err xmlns:p="http://example.com/svc">boom</p:err></detail>
                    </s11:Fault>
                  </s11:Body>
                </s11:Envelope>
                """);

        assertFalse(out.contains("err"), out);
        assertFalse(out.contains("boom"), out);
    }

    private static String filterFaultDetail(SoapVersion version, String soap) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer t = tf.newTransformer();
        StringWriter sw = new StringWriter();
        t.transform(SOAPUtil.getFaultDetailBody(new ByteArrayInputStream(soap.getBytes(UTF_8)), version), new StreamResult(sw));
        return sw.toString();
    }
}
