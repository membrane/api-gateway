package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.annot.MCElement;
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

@MCElement(name="sampleSoapService")
public class SampleSoapService extends AbstractInterceptor {

    public SampleSoapService() {
        name = "SampleSoapService";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        try {
            String cityName = getElementAsString(exc.getRequest().getBodyAsStream(), "city");
            exc.setResponse(Response.ok(getResponse(cityName)).header("Content-Type", "application/xml").build());
        } catch (Exception e) {
            exc.setResponse(Response.ok(getSoapFault("city element not found")).header("Content-Type", "application/xml").build());
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

    public static String getElementAsString(InputStream is, String localName) throws Exception {
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
        throw new Exception();
    }

    public static String getResponse(String result) throws ParserConfigurationException, TransformerException {
        if(result.equals("Bonn") || result.equals("London") || result.equals("New York")) {
            // DocumentBuilderFactory is not guaranteed to be thread safe
            // https://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.5/api/javax/xml/parsers/DocumentBuilderFactory.html
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document responseDoc = builder.newDocument();
            Element envElement = responseDoc.createElementNS("http://schemas.xmlsoap.org/soap/envelope/", "s:Envelope");
            envElement.setAttribute("xmlns:s", "http://schemas.xmlsoap.org/soap/envelope/");
            envElement.setAttribute("xmlns:cs", "https://predic8.de/city-service");
            Element body = responseDoc.createElement("s:Body");
            Element cityDetailsRes = responseDoc.createElement("cs:cityDetails");
            Element country = responseDoc.createElement("cs:country");
            Element population = responseDoc.createElement("cs:population");
            country.appendChild(responseDoc.createTextNode(getCountry(result)));
            population.appendChild(responseDoc.createTextNode(String.valueOf(getPopulation(result))));
            cityDetailsRes.appendChild(country);
            cityDetailsRes.appendChild(population);
            body.appendChild(cityDetailsRes);
            envElement.appendChild(body);
            responseDoc.appendChild(envElement);
            return getString(responseDoc);
        }else{
            return getSoapFault("No city data available. Try Bonn, London or new York");
        }
    }

    private static String getString(Document responseDoc) throws TransformerException {
        TransformerFactory tfFactory = TransformerFactory.newInstance();
        Transformer tf = tfFactory.newTransformer();
        tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        DOMSource source = new DOMSource(responseDoc);
        StringWriter writer = new StringWriter();
        StreamResult reswr = new StreamResult(writer);
        tf.transform(source, reswr);
        return writer.toString();
    }

    private static int getPopulation(String city) {
        return switch (city) {
            case "Bonn" -> 84000000;
            case "London" -> 56000000;
            case "New York" -> 332000000;
            default -> 0;
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