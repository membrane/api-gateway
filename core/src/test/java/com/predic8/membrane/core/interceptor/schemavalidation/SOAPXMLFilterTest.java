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

import com.predic8.membrane.core.util.MessageUtil;
import org.junit.jupiter.api.Test;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SOAPXMLFilterTest {

    /**
     * The SOAP {@code <Body>} wrapper must be stripped, but a domain element that is itself
     * named {@code Body} (in a non-SOAP namespace) must be preserved — otherwise schema
     * validation of the extracted payload fails with cvc-complex-type.2.4.a.
     */
    @Test
    void keepsDomainBodyElement() throws Exception {
        String out = filterSoapBody("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <req:Request xmlns:req="http://example.com/svc">
                      <req:Body>
                        <req:SearchCriteria>abc</req:SearchCriteria>
                      </req:Body>
                    </req:Request>
                  </soapenv:Body>
                </soapenv:Envelope>
                """);

        assertFalse(out.contains("Envelope"), out);
        assertTrue(out.contains("Request"), out);
        assertTrue(out.contains("Body"), out);          // the domain Body survived
        assertTrue(out.contains("SearchCriteria"), out);
    }

    @Test
    void keepsDomainBodyElementSoap12() throws Exception {
        String out = filterSoapBody("""
                <soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope">
                  <soapenv:Body>
                    <req:Request xmlns:req="http://example.com/svc">
                      <req:Body><req:SearchCriteria>x</req:SearchCriteria></req:Body>
                    </req:Request>
                  </soapenv:Body>
                </soapenv:Envelope>
                """);

        assertFalse(out.contains("Envelope"), out);
        assertTrue(out.contains("Body"), out);
        assertTrue(out.contains("SearchCriteria"), out);
    }

    private static String filterSoapBody(String soap) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer t = tf.newTransformer();
        StringWriter sw = new StringWriter();
        t.transform(MessageUtil.getSOAPBody(new ByteArrayInputStream(soap.getBytes(UTF_8))), new StreamResult(sw));
        return sw.toString();
    }
}
