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
package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.slf4j.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.namespace.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor.*;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.IOUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class SampleSoapInterceptorTest {

    private static final Logger log = LoggerFactory.getLogger(SampleSoapInterceptorTest.class.getName());

    private static SampleSoapServiceInterceptor service;
    private static final Exchange exc = new Exchange(null);

    private static ServiceProxy serviceProxy;

    @BeforeAll
    static void setUp() {
        service = new SampleSoapServiceInterceptor();
        serviceProxy = new ServiceProxy(new ServiceProxyKey("localhost", ".*", ".*", 3011), "thomas-bayer.com", 80);
    }

    @Test
    void notFoundTest() throws Exception {
        exc.setRequest(new Request.Builder().contentType(TEXT_XML).post("/foo")
                .body(toByteArray(requireNonNull(this.getClass().getResourceAsStream("/soap-sample/wrong-request.xml")))).build());
        service.handleRequest(exc);
        assertEquals(SampleSoapServiceInterceptor.getSoapFault("Resource Not Found", "404", "Cannot parse SOAP message. Request should contain e.g. <name>Bonn</name>"), exc.getResponse().getBody().toString());
        // System.out.println(exc.getResponse().getBody().toString());
    }

    @ParameterizedTest
    @CsvSource({
            "/foo?bar, false",
            "/foo?content-wsdl, true",
            "/foo?wSdL, true",
            "/foo?w-sdl, false"
    })
    void isWsdlTest(String path, boolean expected) throws Exception {
        exc.setRequest(new Request.Builder().get(path).build());
        assertEquals(expected, isWSDLRequest(exc));
    }

    @Test
    void wsdlTest() throws Exception {
        exc.setRequest(new Request.Builder().contentType(TEXT_XML).get("/bar?wsdl").header("Host", "Host").build());
        exc.setRule(serviceProxy);
        exc.setOriginalRequestUri("/foo");
        service.handleRequest(exc);
        // System.out.println(exc.getResponse().getBody().toString());
        assertTrue(exc.getResponse().getBody().toString().contains("Host"));
    }

    @ParameterizedTest
    @CsvSource({
            "/foo?bar, /foo",
            "/foo?content-wsdl, /foo",
            "/foo?wSdL, /foo",
            "/foo?w-sdl, /foo"
    })
    void getPathWithoutParamTest(String path, String expected) {
        assertEquals(getPathWithoutParam(path), expected);
    }


    @Test
    void methodTest() throws Exception {
        exc.setRequest(new Request.Builder().contentType(TEXT_XML).get("/foo").build());
        service.handleRequest(exc);
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Use POST to access the service."));
    }


    private void testValidRequest(String requestFileName, String country, String population) throws Exception {
        try(InputStream requestStream = getClass().getResourceAsStream("/soap-sample/" + requestFileName)) {
            exc.setRequest(new Request.Builder()
                    .contentType(TEXT_XML)
                    .body(toByteArray(requireNonNull(requestStream)))
                    .post("/foo").build());
            service.handleRequest(exc);
            assertTrue(compareXmlStrings(exc.getResponse().getBody().toString(), country, population));
        }
    }

    @Test
    void validRequest1Test() throws Exception {
        testValidRequest("soap-request-bonn.xml", "Germany", "327000");
    }

    @Test
    void validRequest2Test() throws Exception {
        testValidRequest("soap-request-london.xml", "England", "8980000");
    }

    @Test
    void validRequest3Test() throws Exception {
        testValidRequest("soap-request-new-york.xml", "USA", "8460000");
    }

    private boolean compareXmlStrings(String xml, String country, String population) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(inputStream));

            XPath xPath = XPathFactory.newInstance().newXPath();
            xPath.setNamespaceContext(new NamespaceContext() {
                @Override
                public Iterator<String> getPrefixes(String namespaceURI) {
                    return null;
                }

                @Override
                public String getPrefix(String namespaceURI) {
                    if ("http://schemas.xmlsoap.org/soap/envelope/".equals(namespaceURI)) {
                        return "s";
                    } else if ("https://predic8.de/city-service".equals(namespaceURI)) {
                        return "cs";
                    }
                    return null;
                }

                @Override
                public String getNamespaceURI(String prefix) {
                    if ("s".equals(prefix)) {
                        return "http://schemas.xmlsoap.org/soap/envelope/";
                    } else if ("cs".equals(prefix)) {
                        return "https://predic8.de/city-service";
                    }
                    return null;
                }
            });

            Node countryNode = (Node) xPath.evaluate(
                    String.format("/s:Envelope/s:Body/cs:getCityResponse/country[text()='%s']", country),
                    document,
                    XPathConstants.NODE
            );
            Node populationNode = (Node) xPath.evaluate(
                    String.format("/s:Envelope/s:Body/cs:getCityResponse/population[text()='%s']", population),
                    document,
                    XPathConstants.NODE
            );

            return country.equals(countryNode != null ? countryNode.getTextContent() : null)
                    && population.equals(populationNode != null ? populationNode.getTextContent() : null);


        } catch (Exception e) {
            log.error("Error comparing XML: ",e);
            return false;
        }
    }
}
