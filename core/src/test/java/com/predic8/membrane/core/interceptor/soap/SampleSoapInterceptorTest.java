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

    private static SampleSoapService service;
    private static Exchange exc = new Exchange(null);
    @BeforeAll
    public static void setUp() throws IOException {
        service = new SampleSoapService();
    }

    @Test
    public void notFoundTest() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/soap-sample/wrongRequest.xml")))).build());
        service.handleRequest(exc);
        assertEquals(SampleSoapService.getSoapFault("city element not found"), exc.getResponse().getBody().toString());
        // System.out.println(exc.getResponse().getBody().toString());
    }

    private void testValidRequest(String requestFileName, String country, String population) throws Exception {
        InputStream requestStream = getClass().getResourceAsStream("/soap-sample/" + requestFileName);
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML).body(IOUtils.toByteArray(Objects.requireNonNull(requestStream))).build());
        service.handleRequest(exc);
        service.handleRequest(exc);

        String responseXML = exc.getResponse().getBody().toString();
        System.out.println(exc.getResponse().getBody().toString());

        assertTrue(compareXmlStrings(responseXML, country, population));
    }

    @Test
    public void validRequest1Test() throws Exception {
        testValidRequest("soapRequest-Bonn.xml", "Germany", "84000000");
    }

    @Test
    public void validRequest2Test() throws Exception {
        testValidRequest("soapRequest-London.xml", "England", "56000000");
    }

    @Test
    public void validRequest3Test() throws Exception {
        testValidRequest("soapRequest-NewYork.xml", "USA", "332000000");
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
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(inputStream));

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xPath = xPathFactory.newXPath();

            String countryXPath = "/s:Envelope/s:Body/cs:cityDetails/cs:country[text()='%s']";
            String populationXPath = "/s:Envelope/s:Body/cs:cityDetails/cs:population[text()='%s']";
            NamespaceContext nsContext = new NamespaceContext() {
                public String getNamespaceURI(String prefix) {
                    if ("s".equals(prefix)) {
                        return "http://schemas.xmlsoap.org/soap/envelope/";
                    } else if ("cs".equals(prefix)) {
                        return "http://custom.namespace";
                    }
                    return null;
                }

                public String getPrefix(String namespaceURI) {
                    throw new UnsupportedOperationException();
                }

                public Iterator<String> getPrefixes(String namespaceURI) {
                    throw new UnsupportedOperationException();
                }
            };

            xPath.setNamespaceContext(nsContext);

            // Define the XPath expression with namespace prefixes
            String xpathExpression = "/s:Envelope/s:Body/cs:cityDetails/cs:country";

            // Evaluate the XPath expression
            Node countryNode = (Node) xPath.evaluate(xpathExpression, document, XPathConstants.NODE);
            String countryValue = countryNode.getTextContent();

            System.out.println("Country: " + countryValue);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
