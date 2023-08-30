package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.*;
import java.io.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.XMLUtil.*;
import static javax.xml.stream.XMLStreamConstants.*;

@MCElement(name = "sampleSoapService")
public class SampleSoapServiceInterceptor extends AbstractInterceptor {

    public static final String CITY_SERVICE_NS = "https://predic8.de/city-service";

    public SampleSoapServiceInterceptor() {
        name = "SampleSoapService";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        try {
            exc.setResponse(ok(getResponse(getCity(exc))).contentType(APPLICATION_XML).build());
        } catch (Exception e) {
            exc.setResponse(ok(getSoapFault("Cannot parse SOAP message")).contentType(APPLICATION_XML).build());
        }
        return RETURN;
    }

    private static String getCity(Exchange exc) throws Exception {
        return getElementAsString(exc.getRequest().getBodyAsStream(), "city");
    }

    public static String getSoapFault(String error) {
        // Multiline String """ ... """
        // soapenv => s11
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


    // Test: Make String => InputStream
    public static String getElementAsString(InputStream is, String localName) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance(); // TODO Comment about Threadsafe
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

    public static String getResponse(String city) throws ParserConfigurationException, TransformerException {
        try {
            return xml2string(createResponse(city));
        } catch (Exception e) {
            return getSoapFault("Do not know %s. Try Bonn, London or new York".formatted(city)); // Todo abgleichen
        }
    }

    private static Document createResponse(String city) throws Exception {
        // DocumentBuilderFactory is not guaranteed to be thread safe
        // https://docs.oracle.com/cd/E17802_01/webservices/webservices/docs/1.5/api/javax/xml/parsers/DocumentBuilderFactory.html
        Document res = createDocumentBuilder().newDocument();
        res.appendChild(createResponseEnvelope(city, res));
        return res;
    }

    private static Element createResponseEnvelope(String city, Document res) throws Exception {
        Element env = res.createElementNS(SOAP11_NS, "s:Envelope");
        env.setAttribute("xmlns:s", SOAP11_NS);
        env.setAttribute("xmlns:cs", CITY_SERVICE_NS);
        env.appendChild(createBody(city, res));
        return env;
    }

    private static Element createBody(String city, Document res) throws Exception {
        Element body = res.createElement("s:Body");
        body.appendChild(createCityDetails(city, res));
        return body;
    }

    private static Element createCityDetails(String city, Document res) throws Exception {
        Element details = res.createElement("cs:cityDetails");
        details.appendChild(createCountry(city, res));
        details.appendChild(createPopulation(city, res));
        return details;
    }

    private static Element createPopulation(String city, Document res) throws Exception {
        Element pop = res.createElement("cs:population");
        pop.appendChild(res.createTextNode(String.valueOf(getPopulation(city))));
        return pop;
    }

    private static Element createCountry(String city, Document res) {
        Element country = res.createElement("cs:country");
        country.appendChild(res.createTextNode(getCountry(city)));
        return country;
    }

    private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    // Todo Put cities in Map<String,City>
    // record City(Name,Pop,Country)
    private static int getPopulation(String city) throws Exception {
        return switch (city) {
            case "Bonn" -> 300_000;
            case "Manila" -> 1000;
            case "Da Nang" -> 1100;
            case "Bielefeld" -> 1222;
            case "London" -> 56_000_000;
            case "New York" -> 332000000;
            default -> throw new Exception("What city?");
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