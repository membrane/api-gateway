/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.multipart.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.Server;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.text.StringEscapeUtils.escapeXml11;

public class SOAPUtil {

    private static final Logger log = LoggerFactory.getLogger(SOAPUtil.class.getName());
    public static final SOAPAnalysisResult NO_SOAP_RESULT = new SOAPAnalysisResult(false, false, null, null);

    public static boolean isSOAP(XMLInputFactory xmlInputFactory, XOPReconstitutor xopr, Message msg) {
        try {
            XMLEventReader parser;
            synchronized (xmlInputFactory) {
                parser = xmlInputFactory.createXMLEventReader(xopr.reconstituteIfNecessary(msg));
            }

            while (parser.hasNext()) {
                XMLEvent event = parser.nextEvent();
                if (event.isStartElement()) {
                    QName name = ((StartElement) event).getName();
                    return (isSOAP11Element(name)
                            || isSOAP12Element(name)) &&
                           "Envelope".equals(name.getLocalPart());
                }
            }
        } catch (Exception e) {
            log.warn("Ignoring exception: ", e);
        }
        return false;
    }

    public enum FaultCode {Server, Client}

    public static Response createSOAPValidationErrorResponse(FaultCode code, String faultstring, String detail) {
        Response response = new Response();
        response.setStatusCode(400);
        response.setStatusMessage("Bad request");
        response.setHeader(HttpUtil.createHeaders(TEXT_XML_UTF8));
        response.setBodyContent(getFaultSOAP11Body(code, faultstring, detail).getBytes(UTF_8));
        return response;
    }

    public static String getFaultSOAP11Body(String detail) {
        return getFaultSOAP11Body(Server,"Internal Server Error", detail );
    }

    public static String getFaultSOAP11Body(FaultCode code, String faultstring, String detail) {

        return """
                  <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                    <s11:Body>
                      <s11:Fault>
                        <faultcode>s11:%s</faultcode>
                        <faultstring>%s</faultstring>
                        <detail>%s</detail>
                      </s11:Fault>
                    </s11:Body>
                  </s11:Envelope>"""
                .formatted(code, escapeXml11(faultstring), escapeXml11(detail))
                .replace("\n", CRLF);
    }

    public static String getFaultSOAP12Body(String title, String text) {


        return """
                <soapenv:Envelope xmlns:soapenv="%s">
                <soapenv:Body>
                <soapenv:Fault>
                <soapenv:Code>
                <soapenv:Value>soapenv:Receiver</soapenv:Value>
                </soapenv:Code>
                <soapenv:Reason><soapenv:Text xml:lang="en-US">%s</soapenv:Text></soapenv:Reason>
                <soapenv:Detail><Text>%s</Text></soapenv:Detail>
                </soapenv:Fault>
                </soapenv:Body>
                </soapenv:Envelope>"""
                .formatted(SOAP12_NS, escapeXml11(title), escapeXml11(text))
                .replace("\n", CRLF);
    }

    public record SOAPAnalysisResult(boolean isSOAP, boolean isFault, SoapVersion version, QName soapElement) {
    }

    public static SOAPAnalysisResult analyseSOAPMessage(XMLInputFactory xmlInputFactory, XOPReconstitutor xopr, Message msg) {
        /*
         * 0: waiting for "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
         * 1: waiting for "<soapenv:Body>" (skipping any "<soapenv:Header>")
         * 2: waiting for "<soapenv:Fault>"
         */
        try {
            XMLEventReader parser;
            synchronized (xmlInputFactory) {
                parser = xmlInputFactory.createXMLEventReader(xopr.reconstituteIfNecessary(msg));
            }

            SoapVersion version = null;
            int state = 0;
            while (parser.hasNext()) {
                XMLEvent event = parser.nextEvent();
                if (event.isStartElement()) {
                    QName name = ((StartElement) event).getName();

                    if (state < 2 && !isSOAP11Element(name) && !isSOAP12Element(name)) {
                        return NO_SOAP_RESULT;
                    }

                    if ("Header".equals(name.getLocalPart())) {
                        // skip header
                        readUntilEndTag(parser);
                        continue;
                    }

                    String expected;
                    switch (state) {
                        case 0 -> expected = "Envelope";
                        case 1 -> expected = "Body";
                        case 2 -> expected = "Fault";
                        default -> {
                            return NO_SOAP_RESULT;
                        }
                    }

                    if ("Envelope".equals(name.getLocalPart())) {
                        state = 1;
                        version = getSOAPVersion(name);
                        continue;
                    }

                    if (expected.equals(name.getLocalPart())) {
                        switch (state) {
                            case 0 -> version = getSOAPVersion(name);
                            case 2 -> {
                                return new SOAPAnalysisResult(true, true, version, null);
                            }
                            default -> state++;
                        }
                    } else {
                        if (state == 2) {
                            return new SOAPAnalysisResult(true, false, version, name);
                        }
                        return NO_SOAP_RESULT;
                    }
                }
                if (event.isEndElement())
                    return NO_SOAP_RESULT;
            }
        } catch (Exception e) {
            log.warn("Ignoring exception: ", e);
        }
        return NO_SOAP_RESULT;
    }

    private static SoapVersion getSOAPVersion(QName name) {
        return switch (name.getNamespaceURI()) {
            case SOAP11_NS -> SoapVersion.SOAP11;
            case SOAP12_NS -> SoapVersion.SOAP12;
            default -> SoapVersion.UNKNOWN;
        };
    }

    public static boolean isSOAP12Element(QName name) {
        return SOAP12_NS.equals(name.getNamespaceURI());
    }

    public static boolean isSOAP11Element(QName name) {
        return SOAP11_NS.equals(name.getNamespaceURI());
    }

    private static void readUntilEndTag(XMLEventReader parser) throws XMLStreamException {
        XMLEvent event;
        int stack = 0;
        while (parser.hasNext()) {
            event = parser.nextEvent();
            if (event.isStartElement())
                stack++;
            if (event.isEndElement())
                if (stack == 0)
                    break;
                else
                    stack--;
        }
    }
}
