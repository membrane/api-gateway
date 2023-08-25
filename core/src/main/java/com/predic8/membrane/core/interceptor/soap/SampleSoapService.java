package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import groovy.util.logging.Slf4j;
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

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

public class SampleSoapService extends AbstractInterceptor {
    private static Logger logger = LoggerFactory.getLogger(SampleSoapService.class);
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        try {
            String result = getElementAsString(exc.getRequest().getBodyAsStream(), "city");
            if(result.equals("404")){
                logger.info("city element not found");
            }else{
                getResponse(result);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return CONTINUE;
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

    public static void getResponse(String result) throws ParserConfigurationException, TransformerException {
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
        cityPopulation.appendChild(responseDocument.createTextNode(getPopulation(result)));
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
        transformer.transform(source, res);
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
