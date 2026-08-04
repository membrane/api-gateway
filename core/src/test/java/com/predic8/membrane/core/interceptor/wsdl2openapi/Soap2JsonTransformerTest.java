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
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
                    <bezeichnung>Example Corp</bezeichnung>
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
        assertEquals("Example Corp", details.get("bezeichnung").asText());
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

    // --- Schema-driven type conversion ---

    @Test
    void integerFieldConvertedToNumberWithSchema() throws Exception {
        var schema = new ObjectSchema()
                .addProperty("country", new StringSchema())
                .addProperty("population", new IntegerSchema());
        var json = new Soap2JsonTransformer().transform(CITIES_SOAP11_RESPONSE, schema);
        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("population").isNumber(), "population should be a JSON number");
        assertEquals(3645000L, root.get("population").longValue());
        assertTrue(root.get("country").isTextual(), "country should remain a string");
    }

    @Test
    void decimalFieldConvertedToNumberWithSchema() throws Exception {
        var moneySchema = new ObjectSchema()
                .addProperty("amount", new NumberSchema())
                .addProperty("currency", new StringSchema());
        var articleSchema = new ObjectSchema()
                .addProperty("name", new StringSchema())
                .addProperty("price", moneySchema);
        var responseSchema = new ObjectSchema().addProperty("article", articleSchema);

        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <ns:getResponse xmlns:ns="http://example.com/">
                      <article>
                        <name>foo</name>
                        <price><amount>12</amount><currency>EUR</currency></price>
                      </article>
                    </ns:getResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml, responseSchema);
        JsonNode root = mapper.readTree(json);
        var amount = root.path("article").path("price").path("amount");
        assertTrue(amount.isNumber(), "amount should be a JSON number");
        assertEquals(12.0, amount.doubleValue(), 0.0001);
    }

    @Test
    void missingSchemaFallsBackToString() throws Exception {
        var json = new Soap2JsonTransformer().transform(CITIES_SOAP11_RESPONSE);
        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("population").isTextual(), "without schema, population stays a string");
    }

    @Test
    void unknownFieldWithoutSchemaPropertyRemainsString() throws Exception {
        // Schema has no 'population' property — should fall back to string for that field
        var schema = new ObjectSchema().addProperty("country", new StringSchema());
        var json = new Soap2JsonTransformer().transform(CITIES_SOAP11_RESPONSE, schema);
        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("population").isTextual(), "field not in schema should remain a string");
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

    private static final String SOAP11_FAULT_WITH_DETAIL = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>Client</faultcode>
                  <faultstring>WSDL message validation failed</faultstring>
                  <detail>
                    <validation>
                      <item>
                        <message>cvc-type.3.1.3: The value 'RE-12ssss345' of element 'id' is not valid.</message>
                        <line>1</line>
                        <column>368</column>
                      </item>
                    </validation>
                  </detail>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void soap11ServerFaultThrowsSoapFaultException() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP11_SERVER_FAULT));
        assertEquals("soap:Server", ex.getFaultCode());
        assertEquals("Internal server error", ex.getFaultMessage());
        assertEquals(500, ex.getHttpStatus());
        assertNull(ex.getSoapDetail());
    }

    @Test
    void faultDetailExtractedAndConvertedToMap() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP11_FAULT_WITH_DETAIL));
        assertNotNull(ex.getSoapDetail(), "soapDetail must be present when <detail> exists");
        @SuppressWarnings("unchecked")
        var validation = (java.util.Map<String, Object>) ex.getSoapDetail().get("validation");
        assertNotNull(validation, "soapDetail should contain 'validation' key");
        @SuppressWarnings("unchecked")
        var item = (java.util.Map<String, Object>) validation.get("item");
        assertNotNull(item);
        assertTrue(item.get("message").toString().contains("RE-12ssss345"));
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
    void booleanFieldConvertedToBooleanWithSchema() throws Exception {
        var schema = new ObjectSchema()
                .addProperty("active", new BooleanSchema())
                .addProperty("name", new StringSchema());
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getStatusResponse>
                      <active>true</active>
                      <name>Alice</name>
                    </getStatusResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml, schema);
        JsonNode root = mapper.readTree(json);
        assertTrue(root.get("active").isBoolean(), "active should be a JSON boolean");
        assertTrue(root.get("active").booleanValue());
        assertTrue(root.get("name").isTextual());
    }

    @Test
    void falseValueConvertedToBooleanWithSchema() throws Exception {
        var schema = new ObjectSchema().addProperty("active", new BooleanSchema());
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getStatusResponse>
                      <active>false</active>
                    </getStatusResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, schema));
        assertTrue(root.get("active").isBoolean());
        assertFalse(root.get("active").booleanValue());
    }

    @Test
    void typedArrayItemsConvertedWithSchema() throws Exception {
        var itemSchema = new ObjectSchema().addProperty("id", new IntegerSchema());
        var responseSchema = new ObjectSchema()
                .addProperty("order", new ArraySchema().items(itemSchema));
        var json = new Soap2JsonTransformer().transform(REPEATED_ELEMENTS_RESPONSE, responseSchema);
        JsonNode root = mapper.readTree(json);
        JsonNode orders = root.get("order");
        assertTrue(orders.isArray());
        assertEquals(3, orders.size());
        assertTrue(orders.get(0).get("id").isNumber(), "id inside each order should be a JSON number");
        assertEquals(1L, orders.get(0).get("id").longValue());
        assertEquals(3L, orders.get(2).get("id").longValue());
    }

    private static final String SCALAR_ARRAY_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getTagsResponse>
                  <tag>java</tag>
                  <tag>rest</tag>
                  <tag>api</tag>
                </getTagsResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void repeatedScalarElementsBecomeJsonArray() throws Exception {
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(SCALAR_ARRAY_RESPONSE));

        JsonNode tags = root.get("tag");
        assertTrue(tags.isArray(), "Repeated scalar elements should become a JSON array");
        assertEquals(3, tags.size());
        assertEquals("java", tags.get(0).asText());
        assertEquals("rest", tags.get(1).asText());
        assertEquals("api",  tags.get(2).asText());
    }

    @Test
    void typedScalarArrayItemsConvertedWithSchema() throws Exception {
        var responseSchema = new ObjectSchema()
                .addProperty("tag", new ArraySchema().items(new StringSchema()));

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(SCALAR_ARRAY_RESPONSE, responseSchema));

        JsonNode tags = root.get("tag");
        assertTrue(tags.isArray());
        assertEquals(3, tags.size());
        assertTrue(tags.get(0).isTextual(), "Each item should be a JSON string");
        assertEquals("java", tags.get(0).asText());
    }

    @Test
    void singleScalarElementRemainsScalarNotArray() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getTagsResponse>
                      <tag>java</tag>
                    </getTagsResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml));

        assertTrue(root.get("tag").isTextual(), "A single scalar element must stay a scalar, not become a one-element array");
        assertEquals("java", root.get("tag").asText());
    }

    @Test
    void invalidIntegerFallsBackToStringGracefully() throws Exception {
        var schema = new ObjectSchema().addProperty("population", new IntegerSchema());
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getCityResponse xmlns="https://predic8.de/cities">
                      <country>Germany</country>
                      <population>N/A</population>
                    </getCityResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, schema));
        assertTrue(root.get("population").isTextual(), "unparseable integer should fall back to string");
        assertEquals("N/A", root.get("population").asText());
    }

    @Test
    void wsdlDerivedSchemaConvertsPopulationToNumber() throws Exception {
        var xsdToSchema = new XsdToSchema(citiesDefinitions);
        var outputMessages = citiesDefinitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> "getCity".equals(op.getName()))
                .findFirst()
                .map(op -> op.getMessagesByDirection(Operation.Direction.OUTPUT))
                .orElseThrow();
        var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

        JsonNode root = mapper.readTree(
                new Soap2JsonTransformer().transform(CITIES_SOAP11_RESPONSE, responseSchema));
        assertTrue(root.get("population").isNumber(),
                "WSDL-derived schema should type population as a number");
        assertEquals(3645000L, root.get("population").longValue());
    }

    @Test
    void numericTrueBooleanConvertedWithSchema() throws Exception {
        var schema = new ObjectSchema().addProperty("active", new BooleanSchema());
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getStatusResponse>
                      <active>1</active>
                    </getStatusResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, schema));
        assertTrue(root.get("active").isBoolean(), "active should be a JSON boolean");
        assertTrue(root.get("active").booleanValue(), "XSD '1' must map to JSON true");
    }

    @Test
    void numericFalseBooleanConvertedWithSchema() throws Exception {
        var schema = new ObjectSchema().addProperty("active", new BooleanSchema());
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getStatusResponse>
                      <active>0</active>
                    </getStatusResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, schema));
        assertTrue(root.get("active").isBoolean(), "active should be a JSON boolean");
        assertFalse(root.get("active").booleanValue(), "XSD '0' must map to JSON false");
    }

    // --- XML attributes → @-prefixed JSON properties ---

    private static final String CITY_WITH_ATTRIBUTE_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getCityResponse xmlns="https://predic8.de/cities" id="123">
                  <country>Germany</country>
                </getCityResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void xmlAttributeMappedToAtPrefixedJsonProperty() throws Exception {
        var json = new Soap2JsonTransformer().transform(CITY_WITH_ATTRIBUTE_RESPONSE);

        JsonNode root = mapper.readTree(json);
        assertEquals("123", root.get("@id").asText());
        assertEquals("Germany", root.get("country").asText());
    }

    @Test
    void multipleXmlAttributesAllMappedToAtPrefixedJsonProperties() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getCityResponse xmlns="https://predic8.de/cities" id="123" type="capital">
                      <country>Germany</country>
                    </getCityResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml);

        JsonNode root = mapper.readTree(json);
        assertEquals("123", root.get("@id").asText());
        assertEquals("capital", root.get("@type").asText());
        assertEquals("Germany", root.get("country").asText());
    }

    @Test
    void xmlnsDeclarationsAreNotMappedAsAttributes() throws Exception {
        var json = new Soap2JsonTransformer().transform(CITY_WITH_ATTRIBUTE_RESPONSE);

        JsonNode root = mapper.readTree(json);
        assertNull(root.get("@xmlns"), "xmlns declaration must not leak into JSON output");
    }

    @Test
    void xmlLangAttributeIsNotMapped() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getCityResponse xmlns="https://predic8.de/cities" xml:lang="en">
                      <country>Germany</country>
                    </getCityResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml);

        JsonNode root = mapper.readTree(json);
        assertNull(root.get("@lang"), "xml:lang should be excluded from attribute mapping");
        assertEquals("Germany", root.get("country").asText());
    }

    @Test
    void nestedElementAttributeIsMapped() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getBankResponse xmlns="http://thomas-bayer.com/blz/">
                      <details currency="EUR">
                        <bezeichnung>Example Corp</bezeichnung>
                      </details>
                    </getBankResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml);

        JsonNode root = mapper.readTree(json);
        JsonNode details = root.get("details");
        assertNotNull(details);
        assertEquals("EUR", details.get("@currency").asText());
        assertEquals("Example Corp", details.get("bezeichnung").asText());
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
