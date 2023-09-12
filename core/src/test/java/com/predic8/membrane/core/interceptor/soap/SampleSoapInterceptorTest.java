package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleSoapInterceptorTest {

    private static SampleSoapServiceInterceptor service;
    private static Exchange exc = new Exchange(null);
    @BeforeAll
    public static void setUp() throws IOException {
        service = new SampleSoapServiceInterceptor();
    }

    @Test
    public void notFoundTest() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/soap-sample/wrongRequest.xml")))).build());
        service.handleRequest(exc);
        assertEquals(SampleSoapServiceInterceptor.getSoapFault("Cannot parse SOAP message"), exc.getResponse().getBody().toString());
        // System.out.println(exc.getResponse().getBody().toString());
    }

    private void testValidRequest(String requestFileName, String country, String population) throws Exception {
        InputStream requestStream = getClass().getResourceAsStream("/soap-sample/" + requestFileName);
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML).body(IOUtils.toByteArray(Objects.requireNonNull(requestStream))).build());
        service.handleRequest(exc);
        service.handleRequest(exc);

        String responseXML = exc.getResponse().getBody().toString();
        // System.out.println(exc.getResponse().getBody().toString());

        assertTrue(compareXmlStrings(responseXML, country, population));
    }

    @Test
    public void validRequest1Test() throws Exception {
        testValidRequest("soapRequest-Bonn.xml", "Germany", "83200000");
    }

    @Test
    public void validRequest2Test() throws Exception {
        testValidRequest("soapRequest-London.xml", "England", "55980000");
    }

    @Test
    public void validRequest3Test() throws Exception {
        testValidRequest("soapRequest-NewYork.xml", "USA", "331900000");
    }


    private String getValidResponse(String country, String population) {
        return  "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <s:Body>\n" +
                "    <cs:cityDetails>\n" +
                "      <cs:country>" + country + "</cs:country>\n" +
                "      <cs:population>" + population + "</cs:population>\n" +
                "    </cs:cityDetails>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>\n";
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
                    String.format("/s:Envelope/s:Body/cs:cityDetails/cs:country[text()='%s']", country),
                    document,
                    XPathConstants.NODE
            );
            Node populationNode = (Node) xPath.evaluate(
                    String.format("/s:Envelope/s:Body/cs:cityDetails/cs:population[text()='%s']", population),
                    document,
                    XPathConstants.NODE
            );

            return country.equals(countryNode != null ? countryNode.getTextContent() : null)
                    && population.equals(populationNode != null ? populationNode.getTextContent() : null);


        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
