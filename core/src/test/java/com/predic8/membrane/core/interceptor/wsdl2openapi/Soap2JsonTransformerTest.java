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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;

import static org.junit.jupiter.api.Assertions.*;

class Soap2JsonTransformerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
    }

    private static final String CITIES_SOAP11_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getCityResponse xmlns="https://predic8.de/cities">
                  <country>Germany</country>
                  <population>3645000</population>
                </getCityResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    private static final String CITIES_SOAP12_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <getCityResponse xmlns="https://predic8.de/cities">
                  <country>France</country>
                  <population>2161000</population>
                </getCityResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    private static final String BLZ_SOAP_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getBankResponse xmlns="http://thomas-bayer.com/blz/">
                  <details>
                    <bezeichnung>Deutsche Bank</bezeichnung>
                    <bic>DEUTDEDB</bic>
                    <ort>Berlin</ort>
                    <plz>10117</plz>
                  </details>
                </getBankResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    private static final String REPEATED_ELEMENTS_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getOrdersResponse>
                  <order>
                    <id>1</id>
                  </order>
                  <order>
                    <id>2</id>
                  </order>
                  <order>
                    <id>3</id>
                  </order>
                </getOrdersResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void soap11ResponseParsedToJson() throws Exception {
        var transformer = new Soap2JsonTransformer();
        var json = transformer.transform(CITIES_SOAP11_RESPONSE);

        JsonNode root = mapper.readTree(json);
        assertEquals("Germany", root.get("country").asText());
        assertEquals("3645000", root.get("population").asText());
    }

    @Test
    void soap12ResponseParsedToJson() throws Exception {
        var transformer = new Soap2JsonTransformer();
        var json = transformer.transform(CITIES_SOAP12_RESPONSE);

        JsonNode root = mapper.readTree(json);
        assertEquals("France", root.get("country").asText());
        assertEquals("2161000", root.get("population").asText());
    }

    @Test
    void nestedElementsMappedToNestedJson() throws Exception {
        var transformer = new Soap2JsonTransformer();
        var json = transformer.transform(BLZ_SOAP_RESPONSE);

        JsonNode root = mapper.readTree(json);
        JsonNode details = root.get("details");
        assertNotNull(details, "Nested <details> should become a JSON object");
        assertEquals("Deutsche Bank", details.get("bezeichnung").asText());
        assertEquals("DEUTDEDB", details.get("bic").asText());
        assertEquals("Berlin", details.get("ort").asText());
        assertEquals("10117", details.get("plz").asText());
    }

    @Test
    void repeatedElementsBecomeJsonArray() throws Exception {
        var transformer = new Soap2JsonTransformer();
        var json = transformer.transform(REPEATED_ELEMENTS_RESPONSE);

        JsonNode root = mapper.readTree(json);
        JsonNode orders = root.get("order");
        assertNotNull(orders, "Repeated elements should produce a JSON field");
        assertTrue(orders.isArray(), "Three <order> elements should become a JSON array");
        assertEquals(3, orders.size());
    }

    @Test
    void singleElementRemainsJsonObject() throws Exception {
        var transformer = new Soap2JsonTransformer();
        var json = transformer.transform(CITIES_SOAP11_RESPONSE);

        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("country").isTextual(), "Single element should remain a scalar, not array");
    }

    @Test
    void outputIsValidJson() throws Exception {
        var transformer = new Soap2JsonTransformer();
        var json = transformer.transform(CITIES_SOAP11_RESPONSE);

        assertDoesNotThrow(() -> mapper.readTree(json), "Output should be valid JSON");
    }

    @Test
    void missingBodyThrowsException() {
        var transformer = new Soap2JsonTransformer();
        var invalidSoap = "<root><foo>bar</foo></root>";

        assertThrows(Exception.class, () -> transformer.transform(invalidSoap),
                "Should throw when SOAP Body is missing");
    }

    @Test
    void emptyBodyElementThrowsException() {
        var transformer = new Soap2JsonTransformer();
        var emptyBody = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body/>
                </soap:Envelope>
                """;

        assertThrows(Exception.class, () -> transformer.transform(emptyBody),
                "Should throw when SOAP Body has no response element");
    }

    private static final String SOAP11_SERVER_FAULT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:Server</faultcode>
                  <faultstring>Internal server error</faultstring>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """;

    private static final String SOAP11_CLIENT_FAULT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:Client</faultcode>
                  <faultstring>Invalid BLZ format</faultstring>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """;

    private static final String SOAP12_RECEIVER_FAULT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope">
              <env:Body>
                <env:Fault>
                  <env:Code>
                    <env:Value>env:Receiver</env:Value>
                  </env:Code>
                  <env:Reason>
                    <env:Text xml:lang="en">Processing error</env:Text>
                  </env:Reason>
                </env:Fault>
              </env:Body>
            </env:Envelope>
            """;

    @Test
    void soap11ServerFaultThrowsSoapFaultException() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP11_SERVER_FAULT));
        assertEquals("soap:Server", ex.getFaultCode());
        assertEquals("Internal server error", ex.getFaultMessage());
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    void soap11ClientFaultHasStatus400() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP11_CLIENT_FAULT));
        assertEquals("soap:Client", ex.getFaultCode());
        assertEquals("Invalid BLZ format", ex.getFaultMessage());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void soap12ReceiverFaultThrowsSoapFaultException() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP12_RECEIVER_FAULT));
        assertEquals("env:Receiver", ex.getFaultCode());
        assertEquals("Processing error", ex.getFaultMessage());
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    void doctypeInResponseCausesSaxParseException() {
        var transformer = new Soap2JsonTransformer();
        var xmlWithDoctype = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body><response>test</response></soap:Body>
                </soap:Envelope>
                """;

        assertThrows(XmlParseException.class, () -> transformer.transform(xmlWithDoctype),
                "DOCTYPE should be rejected to prevent XXE");
    }
}
