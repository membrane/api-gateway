package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

public class SampleSoapService extends AbstractInterceptor {
    private static Logger logger = LoggerFactory.getLogger(SampleSoapService.class);
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String result = getElementAsString(exc.getRequest().getBodyAsStream(), "city");
        if(result.equals("404")){
            exc.setResponse(Response.ok(getSoapFault("city element not found")).header("Content-Type", "application/xml").build());
        }else{
            exc.setResponse(Response.ok(getResponse(result, exc)).header("Content-Type", "application/xml").build());
        }
        return RETURN;
    }

    public static String getSoapFault(String error) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "    <soapenv:Body>\n" +
                "        <soapenv:Fault>\n" +
                "            <faultcode>soapenv:Client</faultcode>\n" +
                "            <faultstring>Resource Not Found</faultstring>\n" +
                "            <detail>\n" +
                "                <errorcode>404</errorcode>\n" +
                "                <errormessage>" + error + "</errormessage>\n" +
                "            </detail>\n" +
                "        </soapenv:Fault>\n" +
                "    </soapenv:Body>\n" +
                "</soapenv:Envelope>";
    }

    public static String getElementAsString(InputStream is, String localName) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(is);

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == START_ELEMENT) {
                QName startElementName = reader.getName();
                if (startElementName.getLocalPart().equals(localName)) {
                    return reader.getElementText();
                }
            }
        }
        return "404";
    }

    public static String getResponse(String result, Exchange exc) throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document responseDocument = builder.newDocument();

        Element envelopeElement = responseDocument.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "s:Envelope");
        envelopeElement.setAttribute("xmlns:s", "http://schemas.xmlsoap.org/soap/envelope/");
        Element bodyElement = responseDocument.createElement("s:Body");
        Element cityDetailsResponseElement = responseDocument.createElement("cs:cityDetails");
        Element cityCountry = responseDocument.createElement("cs:country");
        Element cityPopulation = responseDocument.createElement("cs:population");
        cityCountry.appendChild(responseDocument.createTextNode(getCountry(result)));
        exc.setProperty("country", getCountry(result));
        cityPopulation.appendChild(responseDocument.createTextNode(getPopulation(result)));
        exc.setProperty("population", getPopulation(result));
        cityDetailsResponseElement.appendChild(cityCountry);
        cityDetailsResponseElement.appendChild(cityPopulation);
        bodyElement.appendChild(cityDetailsResponseElement);
        envelopeElement.appendChild(bodyElement);
        responseDocument.appendChild(envelopeElement);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        DOMSource source = new DOMSource(responseDocument);
        StreamResult res = new StreamResult(System.out);
        StringWriter writer = new StringWriter();
        StreamResult reswr = new StreamResult(writer);
        transformer.transform(source, res);
        return writer.toString();
    }

    private static String getPopulation(String city) {
        return switch (city) {
            case "Bonn" -> "84 million";
            case "London" -> "56 million";
            case "New York" -> "332 million";
            default -> "Unknown";
        };
    }

    private static String getCountry(String city) {
        return switch (city) {
            case "Bonn" -> "Germany";
            case "London" -> "England";
            case "New York" -> "USA";
            default -> "Unknown";
        };
    }
}
