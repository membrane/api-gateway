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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.*;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.TransformerException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.Constants.SOAP11_NS;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.Utils.getResourceAsStream;
import static com.predic8.membrane.core.util.XMLUtil.xml2string;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

@MCElement(name = "sampleSoapService")
public class SampleSoapServiceInterceptor extends AbstractInterceptor {

    public static final Pattern WSDL_PATH_PARAM = Pattern.compile("(?i).*\\?.*wsdl.*");
    public static final String CITY_SERVICE_NS = "https://predic8.de/cities";

    public SampleSoapServiceInterceptor() {
        name = "SampleSoapService";
    }

    @Override
    public String getShortDescription() {
        return "Provides a simple SOAP service for testing and demonstration purposes.";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (isWSDLRequest(exc)) {
            exc.setResponse(createWSDLResponse(exc));
        } else if(!exc.getRequest().isPOSTRequest()) {
            exc.setResponse(createMethodNotAllowedSOAPFault());
        } else {
            try {
                exc.setResponse(createGetCityResponse(exc));
            } catch (Exception e) {
                exc.setResponse(createResourceNotFoundSOAPFault());
            }
        }
        return RETURN;
    }

    private static Response createResourceNotFoundSOAPFault() throws Exception {
        return ok(getSoapFault("Resource Not Found", "404", "Cannot parse SOAP message. Request should contain e.g. <name>Bonn</name>")).contentType(APPLICATION_XML).build();
    }

    private static Response createGetCityResponse(Exchange exc) throws Exception {
        return ok(getResponse(getCity(exc))).contentType(TEXT_XML).build();
    }

    private static Response createMethodNotAllowedSOAPFault() throws Exception {
        return ok(getSoapFault("Method Not Allowed", "405", "Use POST to access the service.")).contentType(APPLICATION_XML).build();
    }

    private Response createWSDLResponse(Exchange exc) throws XMLStreamException, FileNotFoundException {
        return ok().header(CONTENT_TYPE, TEXT_XML_UTF8)
                   .body(setWsdlServer(
                           getResourceAsStream(this,"/wsdl/city.wsdl"),exc)
                   ).build();
    }


    static boolean isWSDLRequest(Exchange exc) {
        return WSDL_PATH_PARAM.matcher(exc.getRequest().getUri()).matches();
    }

    private static String getCity(Exchange exc) throws Exception {
        return getElementAsString(exc.getRequest().getBodyAsStream(), "name");
    }

    private static final HashMap<String, City> cityMap = new HashMap<>() {{
        put("Bonn", new City("Bonn", 327_000, "Germany"));
        put("Bielefeld", new City("Bielefeld", 333_000, "Germany"));
        put("Manila", new City("Manila", 1_780_000, "Philippines"));
        put("Da Nang", new City("Da Nang", 1_220_000, "Vietnam"));
        put("London", new City("London", 8_980_000, "England"));
        put("New York", new City("New York", 8_460_000, "USA"));
    }};

    public static String getSoapFault(String faultString, String code, String errorMessage) {
        return """
        <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
            <s11:Body>
                <s11:Fault>
                    <faultcode>s11:Client</faultcode>
                    <faultstring>%s</faultstring>
                    <detail>
                        <errorcode>%s</errorcode>
                        <errormessage>%s</errormessage>
                    </detail>
                </s11:Fault>
            </s11:Body>
        </s11:Envelope>
    """.formatted(faultString, code, errorMessage);
    }

    public static String getElementAsString(InputStream is, String localName) throws Exception {
        // MLInputFactory is not required to be thread-safe, ...
        // https://javadoc.io/static/com.sun.xml.ws/jaxws-rt/2.2.10-b140319.1121/com/sun/xml/ws/api/streaming/XMLStreamReaderFactory.Default.html#:~:text=XMLInputFactory%20is%20not%20required%20to,using%20a%20XMLInputFactory%20per%20thread.
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(is);

        while (reader.hasNext()) {
            if (reader.next() == START_ELEMENT) {
                if (reader.getName().getLocalPart().equals(localName)) {
                    return reader.getElementText();
                }
            }
        }
        throw new Exception();
    }

    public static String setWsdlServer(InputStream is, Exchange exc) throws XMLStreamException {
        // XMLEventFactory is not required to be thread-safe, ...
        // https://javadoc.io/static/com.sun.xml.ws/jaxws-rt/2.2.10-b140319.1121/com/sun/xml/ws/api/streaming/XMLStreamReaderFactory.Default.html
        StringWriter modifiedXmlWriter = new StringWriter();
        XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(Objects.requireNonNull(is));
        XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(modifiedXmlWriter);
        XMLEventFactory fac = XMLEventFactory.newInstance();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                if ("address".equals(startElement.getName().getLocalPart())) {
                    writer.add(fac.createStartElement("s", "soap", "address"));
                    writer.add(fac.createAttribute("location", getSOAPAddress(exc)));
                } else {
                    writer.add(event);
                }
            } else {
                writer.add(event);
            }
        }

        return modifiedXmlWriter.toString();
    }

    public static String getSOAPAddress(Exchange exc) {
        return exc.getInboundProtocol() + "://" + exc.getRequest().getHeader().getHost() + getPathWithoutParam(exc.getOriginalRequestUri());
    }

    static String getPathWithoutParam(String exc) {
        return exc.replaceAll("\\?.*$", "");
    }


    public static String getResponse(String city) throws ParserConfigurationException, TransformerException {
        try {
            return xml2string(createResponse(city));
        } catch (Exception e) {
            return getSoapFault("Not Found", "404", "Do not know %s. Try Bonn, London or New York".formatted(city));
        }
    }

    private static Document createResponse(String city) throws Exception {
        // DocumentBuilderFactory is not guaranteed to be thread safe
        // https://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.5/api/javax/xml/parsers/DocumentBuilderFactory.html
        Document res = createDocumentBuilder().newDocument();
        res.appendChild(createResponseEnvelope(city, res));
        return res;
    }

    private static Element createResponseEnvelope(String city, Document res) {
        Element env = res.createElementNS(SOAP11_NS, "s:Envelope");
        env.setAttribute("xmlns:s", SOAP11_NS);
        env.setAttribute("xmlns:cs", CITY_SERVICE_NS);
        env.appendChild(createBody(city, res));
        return env;
    }

    private static Element createBody(String city, Document res) {
        Element body = res.createElement("s:Body");
        body.appendChild(createCityDetails(city, res));
        return body;
    }

    private static Element createCityDetails(String city, Document res) {
        Element details = res.createElement("cs:getCityResponse");
        details.appendChild(createCountry(city, res));
        details.appendChild(createPopulation(city, res));
        return details;
    }

    private static Element createPopulation(String city, Document res) {
        Element pop = res.createElement("population");
        pop.appendChild(res.createTextNode(String.valueOf(cityMap.get(city).population)));
        return pop;
    }

    private static Element createCountry(String city, Document res) {
        Element country = res.createElement("country");
        country.appendChild(res.createTextNode(cityMap.get(city).country));
        return country;
    }

    private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    public record City(String name, int population, String country) {}

}