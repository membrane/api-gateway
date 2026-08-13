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

import java.util.Map;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.VALUE_KEY;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.qualifiedKey;
import static org.junit.jupiter.api.Assertions.*;

class Soap2JsonTransformerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;
    static Definitions crossNamespaceChoiceDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
        crossNamespaceChoiceDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cross-namespace-choice.wsdl");
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

    // --- Named types, reached through a component reference ---

    private static Schema<?> refTo(String componentName) {
        return new Schema<>().$ref("#/components/schemas/" + componentName);
    }

    @Test
    void aLeafTypedByAReferencedComponentIsStillTypedNotStringified() throws Exception {
        var schema = new ObjectSchema()
                .addProperty("country", new StringSchema())
                .addProperty("population", refTo("Population"));

        var json = new Soap2JsonTransformer(Map.of("Population", new IntegerSchema()))
                .transform(CITIES_SOAP11_RESPONSE, schema);

        assertTrue(mapper.readTree(json).get("population").isNumber(),
                "a named XSD type is published once and referenced, so the reference has to be followed");
    }

    @Test
    void aRepeatedReferencedComponentStaysAnArrayOfTypedObjects() throws Exception {
        var schema = new ObjectSchema().addProperty("item", new ArraySchema().items(refTo("Item")));
        var components = Map.<String, Schema<?>>of("Item", new ObjectSchema()
                .addProperty("name", new StringSchema())
                .addProperty("count", new IntegerSchema()));

        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getItemsResponse>
                      <item><name>a</name><count>2</count></item>
                    </getItemsResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode items = mapper.readTree(new Soap2JsonTransformer(components).transform(soapXml, schema)).get("item");

        assertTrue(items.isArray(), "a single occurrence of a repeating field is still an array");
        assertTrue(items.get(0).get("count").isNumber());
    }

    @Test
    void aReferencedSimpleContentComponentKeepsItsValueAndAttributeTypes() throws Exception {
        var schema = new ObjectSchema().addProperty("price", refTo("Money"));
        var components = Map.<String, Schema<?>>of("Money", new ObjectSchema()
                .addProperty(VALUE_KEY, new NumberSchema())
                .addProperty("@rate", new IntegerSchema()));

        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getPriceResponse><price rate="3">12.50</price></getPriceResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode price = mapper.readTree(new Soap2JsonTransformer(components).transform(soapXml, schema)).get("price");

        assertEquals(12.50, price.get(VALUE_KEY).doubleValue(), 0.0001);
        assertTrue(price.get("@rate").isNumber(), "an attribute of a named type is typed too");
    }

    @Test
    void aReferenceToAnUnknownComponentFallsBackToString() throws Exception {
        var schema = new ObjectSchema().addProperty("population", refTo("NotInTheMap"));

        // A reference nothing answers must cost the value's type, not the whole conversion.
        var json = new Soap2JsonTransformer(Map.of()).transform(CITIES_SOAP11_RESPONSE, schema);

        assertTrue(mapper.readTree(json).get("population").isTextual());
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
    void soap11ClientFaultThrowsSoapFaultException() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP11_CLIENT_FAULT));
        assertEquals("soap:Client", ex.getFaultCode());
        assertEquals("Invalid BLZ format", ex.getFaultMessage());
    }

    @Test
    void soap12ReceiverFaultThrowsSoapFaultException() throws Exception {
        var ex = assertThrows(SoapFaultException.class, () -> new Soap2JsonTransformer().transform(SOAP12_RECEIVER_FAULT));
        assertEquals("env:Receiver", ex.getFaultCode());
        assertEquals("Processing error", ex.getFaultMessage());
    }

    @Test
    void faultDetailTypedWhenFaultSchemaSupplied() {
        var faultDetailSchema = new ObjectSchema().addProperty("validation",
                new ObjectSchema().addProperty("item",
                        new ObjectSchema()
                                .addProperty("message", new StringSchema())
                                .addProperty("line", new IntegerSchema())
                                .addProperty("column", new IntegerSchema())));

        var ex = assertThrows(SoapFaultException.class,
                () -> new Soap2JsonTransformer().transform(SOAP11_FAULT_WITH_DETAIL, null, faultDetailSchema));

        assertEquals(1L, itemOf(ex).get("line"));
        assertEquals(368L, itemOf(ex).get("column"));
    }

    @Test
    void faultDetailAllStringsWithoutFaultSchema() {
        var ex = assertThrows(SoapFaultException.class,
                () -> new Soap2JsonTransformer().transform(SOAP11_FAULT_WITH_DETAIL));

        assertEquals("1", itemOf(ex).get("line"));
        assertEquals("368", itemOf(ex).get("column"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> itemOf(SoapFaultException ex) {
        var validation = (Map<String, Object>) ex.getSoapDetail().get("validation");
        return (Map<String, Object>) validation.get("item");
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
    void singleElementRemainsArrayWhenSchemaIsArraySchema() throws Exception {
        var itemSchema = new ObjectSchema().addProperty("id", new IntegerSchema());
        var responseSchema = new ObjectSchema().addProperty("order", new ArraySchema().items(itemSchema));
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getOrdersResponse>
                      <order><id>1</id></order>
                    </getOrdersResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, responseSchema));
        JsonNode orders = root.get("order");
        assertNotNull(orders);
        assertTrue(orders.isArray(), "Single <order> must stay a JSON array when schema is ArraySchema");
        assertEquals(1, orders.size());
        assertTrue(orders.get(0).get("id").isNumber());
        assertEquals(1L, orders.get(0).get("id").longValue());
    }

    @Test
    void singleQualifiedKeyElementRemainsArrayWhenSchemaIsArraySchema() throws Exception {
        // A same-local-name choice alternative is keyed by namespace (XsdToSchema.addChoiceFields),
        // so the array decision must use the schema resolved via that qualified key — looking the
        // plain local name up again finds nothing and collapses the array to a scalar.
        var responseSchema = new ObjectSchema().addProperty(
                qualifiedKey("https://example.com/choice-type-b", "value"),
                new ArraySchema().items(new IntegerSchema()));
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <tns:processAmbiguous xmlns:tns="https://example.com/choice-service"
                                          xmlns:b="https://example.com/choice-type-b">
                      <b:value>42</b:value>
                    </tns:processAmbiguous>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, responseSchema));
        JsonNode values = root.get(qualifiedKey("https://example.com/choice-type-b", "value"));
        assertNotNull(values);
        assertTrue(values.isArray(), "Single <b:value> must stay a JSON array when its qualified schema key is an ArraySchema");
        assertEquals(1, values.size());
        assertTrue(values.get(0).isNumber());
        assertEquals(42L, values.get(0).longValue());
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
    void choiceRefAlternativeTypedCorrectlyFromWsdlDerivedSchema() throws Exception {
        // Build response schema from WSDL: processResult has a choice of a:textResult and b:numericResult.
        // After the addChoiceFields fix, the schema contains both alternatives with correct types.
        var xsdToSchema = new XsdToSchema(crossNamespaceChoiceDefinitions);
        var outputMessages = crossNamespaceChoiceDefinitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> "processInput".equals(op.getName()))
                .findFirst()
                .map(op -> op.getMessagesByDirection(Operation.Direction.OUTPUT))
                .orElseThrow();
        var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

        // SOAP response using the numericResult alternative (from choice-type-b namespace)
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <tns:processResult xmlns:tns="https://example.com/choice-service"
                                       xmlns:b="https://example.com/choice-type-b">
                      <b:numericResult>42</b:numericResult>
                    </tns:processResult>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml, responseSchema);
        JsonNode root = mapper.readTree(json);

        assertNotNull(root.get("numericResult"), "numericResult must appear in JSON");
        assertTrue(root.get("numericResult").isNumber(),
                "WSDL-derived schema must type numericResult as integer via choice ref resolution");
        assertEquals(42L, root.get("numericResult").longValue());
    }

    @Test
    void choiceRefsWithSameLocalNameAreEmittedUnderTheirQualifiedSchemaKey() throws Exception {
        // processAmbiguous has a choice of a:value (string) and b:value (int) — same local
        // name, different namespaces. The schema keys both alternatives with a namespace-qualified
        // key (XsdToSchema.addChoiceFields), so the JSON must use that same key: it is what the
        // published OpenAPI schema advertises, and Json2SoapTransformer strips it back on the
        // request side.
        var xsdToSchema = new XsdToSchema(crossNamespaceChoiceDefinitions);
        var outputMessages = crossNamespaceChoiceDefinitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> "processAmbiguous".equals(op.getName()))
                .findFirst()
                .map(op -> op.getMessagesByDirection(Operation.Direction.OUTPUT))
                .orElseThrow();
        var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

        // SOAP response using the b:value alternative (integer)
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <tns:processAmbiguous xmlns:tns="https://example.com/choice-service"
                                           xmlns:b="https://example.com/choice-type-b">
                      <b:value>42</b:value>
                    </tns:processAmbiguous>
                  </soap:Body>
                </soap:Envelope>
                """;
        var json = new Soap2JsonTransformer().transform(soapXml, responseSchema);
        JsonNode root = mapper.readTree(json);

        String key = qualifiedKey("https://example.com/choice-type-b", "value");
        assertNull(root.get("value"), "the ambiguous local name must not be used as the JSON key");
        assertNotNull(root.get(key), "value must appear under the namespace-qualified schema key");
        assertTrue(root.get(key).isNumber(), "the qualified schema key must type value as integer");
        assertEquals(42L, root.get(key).longValue());
    }

    @Test
    void bothChoiceAlternativesWithSameLocalNameStayDistinctProperties() throws Exception {
        // Both alternatives present at once: keying by the bare local name would merge them into a
        // single property (and silently type one of them with the other's schema).
        var xsdToSchema = new XsdToSchema(crossNamespaceChoiceDefinitions);
        var outputMessages = crossNamespaceChoiceDefinitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> "processAmbiguous".equals(op.getName()))
                .findFirst()
                .map(op -> op.getMessagesByDirection(Operation.Direction.OUTPUT))
                .orElseThrow();
        var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <tns:processAmbiguous xmlns:tns="https://example.com/choice-service"
                                          xmlns:a="https://example.com/choice-type-a"
                                          xmlns:b="https://example.com/choice-type-b">
                      <a:value>text</a:value>
                      <b:value>42</b:value>
                    </tns:processAmbiguous>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, responseSchema));

        JsonNode a = root.get(qualifiedKey("https://example.com/choice-type-a", "value"));
        JsonNode b = root.get(qualifiedKey("https://example.com/choice-type-b", "value"));
        assertNotNull(a, "the choice-type-a alternative must keep its own property");
        assertNotNull(b, "the choice-type-b alternative must keep its own property");
        assertEquals("text", a.asText(), "each alternative must be typed by its own schema");
        assertTrue(b.isNumber(), "each alternative must be typed by its own schema");
        assertEquals(42L, b.longValue());
    }

    @Test
    void unboundedChoiceRefsWithSameLocalNameStayArrays() throws Exception {
        // processAmbiguousList combines both conditions: the alternatives collide on the local name
        // "value" (so they are keyed by namespace) and are maxOccurs="unbounded" (so their schema is
        // an ArraySchema). A single occurrence must still come out as a one-element array.
        var xsdToSchema = new XsdToSchema(crossNamespaceChoiceDefinitions);
        var outputMessages = crossNamespaceChoiceDefinitions.getPortTypes().stream()
                .flatMap(pt -> pt.getOperations().stream())
                .filter(op -> "processAmbiguousList".equals(op.getName()))
                .findFirst()
                .map(op -> op.getMessagesByDirection(Operation.Direction.OUTPUT))
                .orElseThrow();
        var responseSchema = xsdToSchema.convertMessageParts(outputMessages);

        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <tns:processAmbiguousList xmlns:tns="https://example.com/choice-service"
                                              xmlns:b="https://example.com/choice-type-b">
                      <b:value>42</b:value>
                    </tns:processAmbiguousList>
                  </soap:Body>
                </soap:Envelope>
                """;
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, responseSchema));

        JsonNode values = root.get(qualifiedKey("https://example.com/choice-type-b", "value"));
        assertNotNull(values, "value must appear under the namespace-qualified schema key");
        assertTrue(values.isArray(), "an unbounded choice alternative must stay an array even with one occurrence");
        assertEquals(1, values.size());
        assertEquals(42L, values.get(0).longValue());
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

    // --- simpleContent: a value plus attributes ---

    private static final String PRICE_WITH_ATTRIBUTE_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <getPriceResponse>
                  <price currency="EUR">9.99</price>
                  <label>Widget</label>
                </getPriceResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void leafElementWithAttributeKeepsBothValueAndAttribute() throws Exception {
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(PRICE_WITH_ATTRIBUTE_RESPONSE));

        JsonNode price = root.get("price");
        assertEquals("EUR", price.get("@currency").asText(), "the attribute must no longer be dropped");
        assertEquals("9.99", price.get("$value").asText(), "the element's own value moves under $value");
    }

    @Test
    void leafElementWithoutAttributesStaysScalar() throws Exception {
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(PRICE_WITH_ATTRIBUTE_RESPONSE));

        assertTrue(root.get("label").isTextual(), "an element with no attributes must not gain a wrapper object");
        assertEquals("Widget", root.get("label").asText());
    }

    @Test
    void simpleContentValueIsTypedFromTheSchema() throws Exception {
        var priceSchema = new ObjectSchema()
                .addProperty("$value", new NumberSchema())
                .addProperty("@currency", new StringSchema());
        var responseSchema = new ObjectSchema().addProperty("price", priceSchema);

        JsonNode price = mapper.readTree(
                new Soap2JsonTransformer().transform(PRICE_WITH_ATTRIBUTE_RESPONSE, responseSchema)).get("price");

        assertTrue(price.get("$value").isNumber(), "$value must be typed by its own declared schema");
        assertEquals(9.99, price.get("$value").doubleValue());
        assertEquals("EUR", price.get("@currency").asText());
    }

    @Test
    void nilLeafWithAttributesIsStillNull() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soap:Body>
                    <getPriceResponse><price currency="EUR" xsi:nil="true"/></getPriceResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml));

        assertTrue(root.get("price").isNull(), "xsi:nil means no value at all, so nil wins over the wrapper object");
    }

    // --- parents that are not an ObjectSchema instance ---

    @Test
    void plainObjectSchemaStillTypesItsChildren() throws Exception {
        // what a parsed OpenAPI document yields for `type: object` — a Schema, not an ObjectSchema
        Schema<Object> responseSchema = new Schema<>().type("object");
        responseSchema.addProperty("country", new StringSchema());
        responseSchema.addProperty("population", new IntegerSchema());

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(CITIES_SOAP11_RESPONSE, responseSchema));

        assertTrue(root.get("population").isNumber(),
                "a parent carrying properties must type its children even if it is not an ObjectSchema");
        assertEquals(3645000L, root.get("population").longValue());
        assertTrue(root.get("country").isTextual());
    }

    @Test
    void plainObjectSchemaStillDrivesArrayness() throws Exception {
        // a single occurrence, so only the schema can decide this is an array
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <getTagsResponse><tag>java</tag></getTagsResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
        Schema<Object> responseSchema = new Schema<>().type("object");
        responseSchema.addProperty("tag", new ArraySchema().items(new StringSchema()));

        JsonNode tags = mapper.readTree(new Soap2JsonTransformer().transform(soapXml, responseSchema)).get("tag");

        assertTrue(tags.isArray(), "arrayness must survive a non-ObjectSchema parent too");
        assertEquals(1, tags.size());
        assertEquals("java", tags.get(0).asText());
    }

    // --- xsi:nil ---

    private static final String NIL_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <soap:Body>
                <getAccountResponse>
                  <iban>DE89370400440532013000</iban>
                  <closedAt xsi:nil="true"/>
                </getAccountResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    @Test
    void nilElementBecomesJsonNull() throws Exception {
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(NIL_RESPONSE));

        assertTrue(root.hasNonNull("iban"), "the ordinary field is unaffected");
        assertTrue(root.has("closedAt"), "a nil element still appears as a property");
        assertTrue(root.get("closedAt").isNull(), "xsi:nil should become JSON null");
    }

    @Test
    void nilAttributeIsNotExposedAsProperty() throws Exception {
        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(NIL_RESPONSE));

        assertFalse(root.get("closedAt").has("@nil"), "xsi:nil must not surface as an @nil property");
    }

    @Test
    void nilElementBecomesJsonNullEvenWhenSchemaGivesItAType() throws Exception {
        var responseSchema = new ObjectSchema()
                .addProperty("iban", new StringSchema())
                .addProperty("closedAt", new IntegerSchema());

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(NIL_RESPONSE, responseSchema));

        assertTrue(root.get("closedAt").isNull(),
                "a nil element carries no value to coerce, so the schema type must not turn it into 0 or \"\"");
    }

    @Test
    void nilIsRecognisedInItsOneAlternativeLexicalForm() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soap:Body>
                    <getAccountResponse><closedAt xsi:nil="1"/></getAccountResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml));

        assertTrue(root.get("closedAt").isNull(), "xsd:boolean also permits 1 for true");
    }

    @Test
    void nilFalseKeepsTheElementValue() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soap:Body>
                    <getAccountResponse><currency xsi:nil="false">EUR</currency></getAccountResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode root = mapper.readTree(new Soap2JsonTransformer().transform(soapXml));

        assertEquals("EUR", root.get("currency").asText());
    }

    @Test
    void nilElementInsideRepeatedElementsBecomesNullItem() throws Exception {
        var soapXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soap:Body>
                    <getTagsResponse>
                      <tag>java</tag>
                      <tag xsi:nil="true"/>
                      <tag>api</tag>
                    </getTagsResponse>
                  </soap:Body>
                </soap:Envelope>
                """;

        JsonNode tags = mapper.readTree(new Soap2JsonTransformer().transform(soapXml)).get("tag");

        assertTrue(tags.isArray());
        assertEquals(3, tags.size(), "a nil occurrence must still count as an item");
        assertEquals("java", tags.get(0).asText());
        assertTrue(tags.get(1).isNull());
        assertEquals("api", tags.get(2).asText());
    }
}
