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
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;
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
import static java.util.Objects.*;
import static javax.xml.xpath.XPathConstants.STRING;
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
        exc.setRequest(new Request.Builder().contentType(TEXT_XML).get("/bar?wsdl").header("Host", "apollo").build());
        exc.setRule(serviceProxy);
        exc.setOriginalRequestUri("/foo");
        service.handleRequest(exc);
        assertTrue(exc.getResponse().getBody().toString().contains("http://apollo/foo"));
    }

    @ParameterizedTest
    @CsvSource({
            "/foo?bar, /foo",
            "/foo?content-wsdl, /foo",
            "/foo?wSdL, /foo",
            "/foo?w-sdl, /foo"
    })
    void getPathWithoutParamTest(String path, String expected) {
        assertEquals( expected,getPathWithoutParam(path));
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
            DocumentBuilder builder = getDocumentBuilderFactory().newDocumentBuilder();
            Document document = getParse(xml, builder);

            String countryNode = (String) getXPath().evaluate(
                    "/s:Envelope/s:Body/cs:getCityResponse/country",
                    document,
                    STRING
            );
            String populationNode = (String) getXPath().evaluate(
                    "/s:Envelope/s:Body/cs:getCityResponse/population",
                    document,
                    STRING
            );
            return country.equals(countryNode) && population.equals(populationNode);
        } catch (Exception e) {
            log.error("Error comparing XML: ",e);
            return false;
        }
    }

    private static @NotNull XPath getXPath() {
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
                } else if ("https://predic8.de/cities".equals(namespaceURI)) {
                    return "cs";
                }
                return null;
            }

            @Override
            public String getNamespaceURI(String prefix) {
                if ("s".equals(prefix)) {
                    return "http://schemas.xmlsoap.org/soap/envelope/";
                } else if ("cs".equals(prefix)) {
                    return "https://predic8.de/cities";
                }
                return null;
            }
        });
        return xPath;
    }

    private static Document getParse(String xml, DocumentBuilder builder) throws SAXException, IOException {
        return builder.parse(new InputSource(new ByteArrayInputStream(xml.getBytes())));
    }

    private static @NotNull DocumentBuilderFactory getDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }
}
