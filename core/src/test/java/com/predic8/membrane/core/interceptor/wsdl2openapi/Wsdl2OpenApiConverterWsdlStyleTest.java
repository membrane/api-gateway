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

import com.predic8.membrane.core.resolver.StaticStringResolver;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts the correct/expected handling of WSDL-namespace constructs (style, faults, headers,
 * SOAP version, ...) by {@link Wsdl2OpenApiConverter}, as opposed to XSD-to-JSON-Schema
 * conversion, which is covered by {@link XsdToSchemaTest}. Each test embeds its own minimal WSDL
 * as a text block so the scenario under test is fully visible without following a reference to
 * a classpath fixture file.
 *
 */
class Wsdl2OpenApiConverterWsdlStyleTest {

    /** Wrapped document style, service name "GreetingService" — shared by the Info-related tests below. */
    private static final String GREETING_WSDL = """
            <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                         xmlns:xs="http://www.w3.org/2001/XMLSchema"
                         xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                         xmlns:tns="http://example.com/greeting"
                         targetNamespace="http://example.com/greeting"
                         name="GreetingService">

              <types>
                <xs:schema targetNamespace="http://example.com/greeting"
                           elementFormDefault="qualified">

                  <!-- Wrapped document style: one element per message, wrapping all parameters -->
                  <xs:element name="sayHello">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="name" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>

                  <xs:element name="sayHelloResponse">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="greeting" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>

                </xs:schema>
              </types>

              <message name="SayHelloRequest">
                <part name="parameters" element="tns:sayHello"/>
              </message>

              <message name="SayHelloResponse">
                <part name="parameters" element="tns:sayHelloResponse"/>
              </message>

              <portType name="GreetingPortType">
                <operation name="sayHello">
                  <input message="tns:SayHelloRequest"/>
                  <output message="tns:SayHelloResponse"/>
                </operation>
              </portType>

              <binding name="GreetingBinding" type="tns:GreetingPortType">
                <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>

                <operation name="sayHello">
                  <soap:operation soapAction="sayHello"/>
                  <input>
                    <soap:body use="literal"/>
                  </input>
                  <output>
                    <soap:body use="literal"/>
                  </output>
                </operation>
              </binding>

              <service name="GreetingService">
                <port name="GreetingPort" binding="tns:GreetingBinding">
                  <soap:address location="http://example.com/greeting"/>
                </port>
              </service>

            </definitions>""";

    @Test
    void documentLiteralWrappedStyle() throws Exception {
        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), GREETING_WSDL), "/").generateYaml();

        assertTrue(yaml.contains("/say-hello:"), "Operation name should be mapped to a kebab-case path");
        assertTrue(yaml.contains("operationId: \"sayHello\""), "operationId should match the WSDL operation name");
        assertTrue(yaml.contains("name:"), "Request body should expose the wrapped element's 'name' field");
        assertTrue(yaml.contains("greeting:"), "Response body should expose the wrapped element's 'greeting' field");
        assertTrue(yaml.contains("\"200\":"), "Should contain a 200 response");
        assertTrue(yaml.contains("default:"), "Should contain the generic error response");
        assertTrue(yaml.contains("https://github.com/membrane/api-gateway"),
                "info.description should link to the Membrane GitHub page");
        assertTrue(yaml.contains("https://www.membrane-api.io/?oas=1"),
                "info.description should link to the Membrane website");
        assertTrue(yaml.contains("![Logo](https://raw.githubusercontent.com/membrane/api-gateway/master/docs/images/membrane-logo-128.png)"),
                "info.description should embed the Membrane logo as a CommonMark image");
    }

    @Test
    void titleOverrideReplacesServiceName() throws Exception {
        var definitions = Definitions.parse(new StaticStringResolver(), GREETING_WSDL);
        var openAPI = new Wsdl2OpenApiConverter(definitions, "/", Map.of(), "Custom Title", null).generate();

        assertEquals("Custom Title", openAPI.getInfo().getTitle(),
                "an explicit title must override the WSDL service name");
    }

    @Test
    void noTitleOverrideFallsBackToServiceName() throws Exception {
        var definitions = Definitions.parse(new StaticStringResolver(), GREETING_WSDL);
        var openAPI = new Wsdl2OpenApiConverter(definitions, "/", Map.of(), null, null).generate();

        assertEquals("GreetingService", openAPI.getInfo().getTitle());
    }

    @Test
    void descriptionAppearsBeforeGeneratedAdText() throws Exception {
        var definitions = Definitions.parse(new StaticStringResolver(), GREETING_WSDL);
        var openAPI = new Wsdl2OpenApiConverter(definitions, "/", Map.of(), null, "Say hello to the world.").generate();

        String description = openAPI.getInfo().getDescription();
        int userTextIndex = description.indexOf("Say hello to the world.");
        int adIndex = description.indexOf("https://github.com/membrane/api-gateway");

        assertTrue(userTextIndex >= 0, "the configured description must appear in info.description");
        assertTrue(adIndex >= 0, "the generated ad text must still appear");
        assertTrue(userTextIndex < adIndex,
                "the configured description must come before the generated ad text, not after it");
    }

    @Test
    void noDescriptionOverrideProducesOnlyGeneratedAdText() throws Exception {
        var definitions = Definitions.parse(new StaticStringResolver(), GREETING_WSDL);
        var withOverride = new Wsdl2OpenApiConverter(definitions, "/", Map.of(), null, null).generate();
        var withoutAnyArgs = new Wsdl2OpenApiConverter(definitions, "/").generate();

        assertEquals(withoutAnyArgs.getInfo().getDescription(), withOverride.getInfo().getDescription(),
                "omitting the description (via either constructor) must produce identical, unchanged ad text");
        assertFalse(withOverride.getInfo().getDescription().contains("\n\n\n"),
                "no accidental extra blank line when there is no user description to separate from the ad");
    }

    @Test
    void rpcStylePartsBecomeRequestAndResponseFields() throws Exception {
        // RPC style: message parts reference primitive types directly (type=), not a
        // wrapping XSD element (element=) — there is no <types> section at all. Each part is
        // one operation parameter, so all parts of a message should become fields of an
        // implicit wrapper object.
        var wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                             xmlns:tns="http://example.com/calc"
                             targetNamespace="http://example.com/calc"
                             name="CalcService">

                  <message name="AddRequest">
                    <part name="augend" type="xs:int"/>
                    <part name="addend" type="xs:int"/>
                  </message>

                  <message name="AddResponse">
                    <part name="sum" type="xs:int"/>
                  </message>

                  <portType name="CalcPortType">
                    <operation name="add">
                      <input message="tns:AddRequest"/>
                      <output message="tns:AddResponse"/>
                    </operation>
                  </portType>

                  <binding name="CalcBinding" type="tns:CalcPortType">
                    <soap:binding style="rpc" transport="http://schemas.xmlsoap.org/soap/http"/>

                    <operation name="add">
                      <soap:operation soapAction="add"/>
                      <input>
                        <soap:body use="literal" namespace="http://example.com/calc"/>
                      </input>
                      <output>
                        <soap:body use="literal" namespace="http://example.com/calc"/>
                      </output>
                    </operation>
                  </binding>

                  <service name="CalcService">
                    <port name="CalcPort" binding="tns:CalcBinding">
                      <soap:address location="http://example.com/calc"/>
                    </port>
                  </service>

                </definitions>""";

        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), wsdl), "/").generateYaml();

        assertTrue(yaml.contains("/add:"), "Path is still generated regardless of binding style");
        assertTrue(yaml.contains("operationId: \"add\""));
        assertTrue(yaml.contains("augend:"), "Every RPC-style input part should become a request field");
        assertTrue(yaml.contains("addend:"), "Every RPC-style input part should become a request field");
        assertTrue(yaml.contains("sum:"), "The RPC-style output part should become a response field");
    }

    @Test
    void bareDocumentStyleAllPartsBecomeRequestFields() throws Exception {
        // Bare document style: multiple parts per message, each its own top-level element,
        // with no single wrapper element covering all of them. Both parts are operation
        // parameters, so both should end up as fields of the request body.
        var wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                             xmlns:tns="http://example.com/search"
                             targetNamespace="http://example.com/search"
                             name="SearchService">

                  <types>
                    <xs:schema targetNamespace="http://example.com/search"
                               elementFormDefault="qualified">
                      <xs:element name="query" type="xs:string"/>
                      <xs:element name="verbose" type="xs:boolean"/>
                    </xs:schema>
                  </types>

                  <message name="SearchRequest">
                    <part name="query" element="tns:query"/>
                    <part name="verbose" element="tns:verbose"/>
                  </message>

                  <portType name="SearchPortType">
                    <operation name="search">
                      <input message="tns:SearchRequest"/>
                    </operation>
                  </portType>

                  <binding name="SearchBinding" type="tns:SearchPortType">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>

                    <operation name="search">
                      <soap:operation soapAction="search"/>
                      <input>
                        <soap:body use="literal"/>
                      </input>
                    </operation>
                  </binding>

                  <service name="SearchService">
                    <port name="SearchPort" binding="tns:SearchBinding">
                      <soap:address location="http://example.com/search"/>
                    </port>
                  </service>

                </definitions>""";

        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), wsdl), "/").generateYaml();

        assertTrue(yaml.contains("/search:"));
        assertTrue(yaml.contains("query:"), "First bare-style message part should become a request field");
        assertTrue(yaml.contains("verbose:"), "Second bare-style message part should also become a request field");
    }

    @Test
    void multipleFaultsProduceOneOfSchemaOnErrorResponse() throws Exception {
        // Every error is a problem details document, so all faults share the single "default"
        // response. Its details member must be a oneOf of both fault element schemas, so each
        // fault's fields are still represented in the OpenAPI doc.
        var wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                             xmlns:tns="http://example.com/orders"
                             targetNamespace="http://example.com/orders"
                             name="OrderService">

                  <types>
                    <xs:schema targetNamespace="http://example.com/orders"
                               elementFormDefault="qualified">

                      <xs:element name="cancelOrder">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="orderId" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                      <xs:element name="cancelOrderResponse">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="cancelled" type="xs:boolean"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                      <xs:element name="OrderNotFoundFault">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="errorCode" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                      <xs:element name="OrderAlreadyShippedFault">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="invalidField" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                    </xs:schema>
                  </types>

                  <message name="CancelOrderRequest">
                    <part name="parameters" element="tns:cancelOrder"/>
                  </message>

                  <message name="CancelOrderResponse">
                    <part name="parameters" element="tns:cancelOrderResponse"/>
                  </message>

                  <message name="OrderNotFoundFaultMessage">
                    <part name="fault" element="tns:OrderNotFoundFault"/>
                  </message>

                  <message name="OrderAlreadyShippedFaultMessage">
                    <part name="fault" element="tns:OrderAlreadyShippedFault"/>
                  </message>

                  <portType name="OrderPortType">
                    <operation name="cancelOrder">
                      <input message="tns:CancelOrderRequest"/>
                      <output message="tns:CancelOrderResponse"/>
                      <fault name="OrderNotFound" message="tns:OrderNotFoundFaultMessage"/>
                      <fault name="OrderAlreadyShipped" message="tns:OrderAlreadyShippedFaultMessage"/>
                    </operation>
                  </portType>

                  <binding name="OrderBinding" type="tns:OrderPortType">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>

                    <operation name="cancelOrder">
                      <soap:operation soapAction="cancelOrder"/>
                      <input>
                        <soap:body use="literal"/>
                      </input>
                      <output>
                        <soap:body use="literal"/>
                      </output>
                      <fault name="OrderNotFound">
                        <soap:fault name="OrderNotFound" use="literal"/>
                      </fault>
                      <fault name="OrderAlreadyShipped">
                        <soap:fault name="OrderAlreadyShipped" use="literal"/>
                      </fault>
                    </operation>
                  </binding>

                  <service name="OrderService">
                    <port name="OrderPort" binding="tns:OrderBinding">
                      <soap:address location="http://example.com/orders"/>
                    </port>
                  </service>

                </definitions>""";

        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), wsdl), "/").generateYaml();

        assertTrue(yaml.contains("cancelled:"), "Normal output message is still converted");
        assertTrue(yaml.contains("default:"));
        assertEquals(1, yaml.split("default:").length - 1, "Exactly one error response, not one per fault");
        assertTrue(yaml.contains("oneOf:"), "Multiple fault schemas should be combined with oneOf");
        assertTrue(yaml.contains("OrderNotFoundFault:"), "Fault schemas are keyed by fault element name");
        assertTrue(yaml.contains("OrderAlreadyShippedFault:"));
        assertTrue(yaml.contains("errorCode:"), "First fault's schema should be represented");
        assertTrue(yaml.contains("invalidField:"), "Second fault's schema should be represented");
    }

    @Test
    void faultWithMultipleTypeBasedPartsAppearsInFaultSchema() throws Exception {
        var wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                             xmlns:tns="http://example.com/checkout"
                             targetNamespace="http://example.com/checkout">

                  <types>
                    <xs:schema targetNamespace="http://example.com/checkout">
                      <xs:element name="checkout">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="cartId" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>
                      <xs:element name="checkoutResponse">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="orderId" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>
                    </xs:schema>
                  </types>

                  <message name="CheckoutRequest">
                    <part name="parameters" element="tns:checkout"/>
                  </message>
                  <message name="CheckoutResponse">
                    <part name="parameters" element="tns:checkoutResponse"/>
                  </message>
                  <message name="ValidationFaultMessage">
                    <part name="faultCode" type="xs:string"/>
                    <part name="faultReason" type="xs:string"/>
                  </message>

                  <portType name="CheckoutPortType">
                    <operation name="checkout">
                      <input message="tns:CheckoutRequest"/>
                      <output message="tns:CheckoutResponse"/>
                      <fault name="ValidationFault" message="tns:ValidationFaultMessage"/>
                    </operation>
                  </portType>

                  <binding name="CheckoutBinding" type="tns:CheckoutPortType">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                    <operation name="checkout">
                      <soap:operation soapAction="checkout"/>
                      <input><soap:body use="literal"/></input>
                      <output><soap:body use="literal"/></output>
                      <fault name="ValidationFault">
                        <soap:fault name="ValidationFault" use="literal"/>
                      </fault>
                    </operation>
                  </binding>

                  <service name="CheckoutService">
                    <port name="CheckoutPort" binding="tns:CheckoutBinding">
                      <soap:address location="http://example.com/checkout"/>
                    </port>
                  </service>

                </definitions>""";

        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), wsdl), "/").generateYaml();

        assertTrue(yaml.contains("default:"), "Error response must be present for the fault");
        assertTrue(yaml.contains("faultCode:"), "First type-based part must appear in the fault schema");
        assertTrue(yaml.contains("faultReason:"), "Second type-based part must appear in the fault schema");
    }

    /** Shared by the two soap:header tests below: one operation, one header part, one body part. */
    private static final String SECURE_SERVICE_WSDL = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                             xmlns:tns="http://example.com/secure"
                             targetNamespace="http://example.com/secure"
                             name="SecureService">

                  <types>
                    <xs:schema targetNamespace="http://example.com/secure"
                               elementFormDefault="qualified">

                      <xs:element name="doWork">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="payload" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                      <xs:element name="doWorkResponse">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="result" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                      <xs:element name="sessionToken" type="xs:string"/>

                    </xs:schema>
                  </types>

                  <message name="DoWorkRequest">
                    <part name="parameters" element="tns:doWork"/>
                  </message>

                  <message name="DoWorkResponse">
                    <part name="parameters" element="tns:doWorkResponse"/>
                  </message>

                  <message name="AuthHeader">
                    <part name="token" element="tns:sessionToken"/>
                  </message>

                  <portType name="SecurePortType">
                    <operation name="doWork">
                      <input message="tns:DoWorkRequest"/>
                      <output message="tns:DoWorkResponse"/>
                    </operation>
                  </portType>

                  <binding name="SecureBinding" type="tns:SecurePortType">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>

                    <operation name="doWork">
                      <soap:operation soapAction="doWork"/>
                      <input>
                        <soap:header message="tns:AuthHeader" part="token" use="literal"/>
                        <soap:body use="literal"/>
                      </input>
                      <output>
                        <soap:body use="literal"/>
                      </output>
                    </operation>
                  </binding>

                  <service name="SecureService">
                    <port name="SecurePort" binding="tns:SecureBinding">
                      <soap:address location="http://example.com/secure"/>
                    </port>
                  </service>

                </definitions>""";

    @Test
    void soapHeaderBecomesHeaderParameter() throws Exception {
        // soap:header binds a message part that travels in the SOAP header, out-of-band from
        // the body. It should surface as an OpenAPI "in: header" parameter on the operation,
        // not as a body field, and not be dropped.
        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), SECURE_SERVICE_WSDL), "/").generateYaml();

        assertTrue(yaml.contains("payload:"), "Body part is still converted");
        assertTrue(yaml.contains("in: \"header\""), "soap:header part should become an OpenAPI header parameter");
        assertTrue(yaml.contains("name: \"token\""), "Header parameter should be named after its message part");
    }

    @Test
    void pathAndHeaderParametersCoexist() throws Exception {
        // A templated path plus a soap:header yields two parameter sources for the same
        // operation. Both must end up in the parameter list — a path segment "{id}" without a
        // declared path parameter is an invalid OpenAPI document.
        var settings = new OperationSettings();
        settings.setMethod("GET");
        settings.setPath("work/{id}");
        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), SECURE_SERVICE_WSDL), "/",
                Map.of("doWork", settings)).generateYaml();

        assertTrue(yaml.contains("/work/{id}:"), "Templated path should be used as the path key");
        assertTrue(yaml.contains("in: \"path\""), "Path parameter must not be dropped");
        assertTrue(yaml.contains("name: \"id\""), "Path parameter should be named after the template variable");
        assertTrue(yaml.contains("in: \"header\""), "soap:header parameter must still be present");
        assertTrue(yaml.contains("name: \"token\""), "Header parameter should be named after its message part");
    }

    @Test
    void soap12BindingProducesSameOutputAsSoap11() throws Exception {
        var wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/"
                             xmlns:tns="http://example.com/greeting12"
                             targetNamespace="http://example.com/greeting12"
                             name="Greeting12Service">

                  <types>
                    <xs:schema targetNamespace="http://example.com/greeting12"
                               elementFormDefault="qualified">

                      <xs:element name="sayHello">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                      <xs:element name="sayHelloResponse">
                        <xs:complexType>
                          <xs:sequence>
                            <xs:element name="greeting" type="xs:string"/>
                          </xs:sequence>
                        </xs:complexType>
                      </xs:element>

                    </xs:schema>
                  </types>

                  <message name="SayHelloRequest">
                    <part name="parameters" element="tns:sayHello"/>
                  </message>

                  <message name="SayHelloResponse">
                    <part name="parameters" element="tns:sayHelloResponse"/>
                  </message>

                  <portType name="GreetingPortType">
                    <operation name="sayHello">
                      <input message="tns:SayHelloRequest"/>
                      <output message="tns:SayHelloResponse"/>
                    </operation>
                  </portType>

                  <binding name="GreetingBinding" type="tns:GreetingPortType">
                    <soap12:binding style="document" transport="http://schemas.xmlsoap.org/soap12/http"/>

                    <operation name="sayHello">
                      <soap12:operation soapAction="sayHello"/>
                      <input>
                        <soap12:body use="literal"/>
                      </input>
                      <output>
                        <soap12:body use="literal"/>
                      </output>
                    </operation>
                  </binding>

                  <service name="Greeting12Service">
                    <port name="GreetingPort" binding="tns:GreetingBinding">
                      <soap12:address location="http://example.com/greeting12"/>
                    </port>
                  </service>

                </definitions>""";

        var yaml = new Wsdl2OpenApiConverter(Definitions.parse(new StaticStringResolver(), wsdl), "/").generateYaml();

        // SOAP version only affects the wire envelope, not the JSON/OpenAPI shape, so a
        // SOAP 1.2 binding should produce the same output as the equivalent SOAP 1.1 WSDL
        // (see documentLiteralWrappedStyle).
        assertTrue(yaml.contains("/say-hello:"));
        assertTrue(yaml.contains("operationId: \"sayHello\""));
        assertTrue(yaml.contains("name:"));
        assertTrue(yaml.contains("greeting:"));
        assertTrue(yaml.contains("\"200\":"));
        assertTrue(yaml.contains("default:"));
    }

}
