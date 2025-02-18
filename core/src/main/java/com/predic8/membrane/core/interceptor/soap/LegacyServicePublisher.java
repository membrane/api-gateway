/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.wsdl.WSDLParser;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.http.Request.METHOD_GET;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATTERN_UI;
import static com.predic8.membrane.core.util.CollectionsUtil.mapOf;
import static java.lang.String.valueOf;
import static javax.xml.transform.OutputKeys.*;

@MCElement(name = "legacyServicePublisher")
public class LegacyServicePublisher extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LegacyServicePublisher.class);

    private final List<WebServiceOASWrapper> services = new ArrayList<>();
    private final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();

    private String wsdlLocation;
    private OpenAPIPublisher publisher;

    // TODO These should probably be extracted into utils and then replaced in SoapBodyTemplateInterceptor as well.
    public static final String SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP11_PREFIX = """
                <s11:Envelope xmlns:s11="%s">
                    <s11:Body>
                """.formatted(SOAP_ENVELOPE);
    private static final String SOAP11_POSTFIX = """
                    </s11:Body>
                </s11:Envelope>
                """;

    public LegacyServicePublisher() {
        documentBuilderFactory.setNamespaceAware(true);
    }

    @Override
    public void init() {
        super.init();
        new WSDLParser().parse(wsdlLocation).getServices().forEach(svc -> services.add(new WebServiceOASWrapper(svc)));
        try {
            publisher = new OpenAPIPublisher(mapOf(services.stream().flatMap(WebServiceOASWrapper::getApiRecords)));
        } catch (Exception e) {
            log.error("OpenAPI Publisher failed to initialize.", e);
            throw new ConfigurationException("Unable to initialize Swagger UI.", e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (exc.getRequest().getMethod().equals(METHOD_GET)) {
            return handleOpenAPIServing(exc);
        }
        if (exc.getRequest().getMethod().equals(METHOD_POST)) {
            return handleServiceRouting(exc);
        }
        ProblemDetails.user(router.isProduction(), getDisplayName())
                .title("Invalid HTTP method")
                .buildAndSetResponse(exc);
        return RETURN;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        String accept = exc.getRequest().getHeader().getAccept();
        if (APPLICATION_XML.equals(accept)) {
            setSOAPResponseToXML(exc);
        } else if (APPLICATION_JSON.equals(accept)) {
            setSOAPResponseToJSON(exc);
        }
        return CONTINUE;
    }

    private Outcome handleServiceRouting(Exchange exc) {
        if (exc.getRequest().isXML()) {
            setSOAPRequestFromXML(exc);
        } else if (exc.getRequest().isJSON()) {
            setSOAPRequestFromJSON(exc);
        }
        return CONTINUE;
    }

    // TODO Probably has to be adjusted according to the PortMapping
    //  String baseUrl = getBaseUrl(exc.getRequest().getUri(), 1);

    private void setSOAPRequestFromXML(Exchange exc) {
        String xml = exc.getRequest().getBodyAsStringDecoded().replaceFirst("<\\?xml.*?\\?>", "");
        if (!xml.contains(":Envelope")) {
            exc.getRequest().setBodyContent(
                buildSoapBody(xml).getBytes()
            );
        }
    }

    private void setSOAPRequestFromJSON(Exchange exc) {
        exc.getRequest().setBodyContent(
                buildSoapBody(convertJsonToXml(exc.getRequest().getBodyAsStringDecoded())).getBytes()
        );
    }

    private String buildSoapBody(String content) {
        return SOAP11_PREFIX + content + SOAP11_POSTFIX;
    }

    // TODO /////////////////////////////////////////////////////////////

    private void setSOAPResponseToXML(Exchange exc) {
        exc.getResponse().setBodyContent(
            extractBodyFromSoap(
                exc.getResponse().getBodyAsStringDecoded()
            ).getBytes()
        );
        exc.getResponse().getHeader().setContentType(APPLICATION_XML);
    }

    private void setSOAPResponseToJSON(Exchange exc) {
        exc.getResponse().setBodyContent(
            convertXmlToJson(
                extractBodyFromSoap(
                    exc.getResponse().getBodyAsStringDecoded()
                )
            ).getBytes()
        );
        exc.getResponse().getHeader().setContentType(APPLICATION_JSON);
    }

    private PortMapping getMapping(String baseUrl) {
        for (WebServiceOASWrapper service : services) {
            PortMapping mapping = service.getMapping(baseUrl);
            if (mapping != null) {
                return mapping;
            }
        }
        // TODO Maybe handle "service not found" user error
        return null;
    }

    String extractBodyFromSoap(String soap) {
        try {
            Node responseNode = domFromString(soap);

            Document newDoc = documentBuilderFactory.newDocumentBuilder().newDocument();
            Element newResponse = newDoc.createElement(responseNode.getLocalName());
            newDoc.appendChild(newResponse);

            appendChildren(responseNode, newDoc, newResponse);

            return domToString(newDoc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String domToString(Document newDoc) throws TransformerException {
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(ENCODING, "UTF-8");
        transformer.setOutputProperty(INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(newDoc), new StreamResult(writer));
        return writer.toString();
    }

    static void appendChildren(Node responseNode, Document newDoc, Element newResponse) {
        NodeList children = responseNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element e = newDoc.createElement(child.getLocalName());
                e.setTextContent(child.getTextContent());
                newResponse.appendChild(e);
            }
        }
    }

    // TODO Maybe better name as this also extracts the body from the SOAP envelope.
    @NotNull Node domFromString(String soap) throws SAXException, IOException, ParserConfigurationException {
        Node bodyNode = documentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soap)))
                .getElementsByTagNameNS(SOAP_ENVELOPE, "Body")
                .item(0);
        // TODO Better Exception
        if (bodyNode == null) throw new RuntimeException("No SOAP Body element found");
        return getFirstRealElement(bodyNode.getFirstChild());
    }

    static @NotNull Node getFirstRealElement(Node responseNode) {
        while (responseNode != null && responseNode.getNodeType() != Node.ELEMENT_NODE) {
            responseNode = responseNode.getNextSibling();
        }
        if (responseNode == null) throw new RuntimeException("Body content is null");
        return responseNode;
    }

    String convertXmlToJson(String xml) {
        JSONObject jsonObject = XML.toJSONObject(xml);
        if (jsonObject.length() == 1) {
            return jsonObject.getJSONObject(jsonObject.keys().next()).toString(2);
        }
        return jsonObject.toString(2);
    }

    private String convertJsonToXml(String json) {
        try {
            StringWriter writer = new StringWriter();
            Transformer tr = transformerFactory.newTransformer();
            tr.setOutputProperty(INDENT, "yes");
            tr.setOutputProperty(OMIT_XML_DECLARATION, "yes");
            tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            tr.transform(new DOMSource(
                            documentBuilderFactory.newDocumentBuilder().parse(
                                    new InputSource(new StringReader(XML.toString(new JSONObject(json)))))
                    ), new StreamResult(writer)
            );
            return writer.toString();
        } catch (TransformerException | SAXException | IOException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private Outcome handleOpenAPIServing(Exchange exc) {
        if (exc.getRequest().getUri().matches(valueOf(PATTERN_UI))) {
            return publisher.handleSwaggerUi(exc);
        }

        try {
            return publisher.handleOverviewOpenAPIDoc(exc, router, log);
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(), getDisplayName())
                    .detail("Error generating OpenAPI overview!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    @MCAttribute
    public void setWsdl(String wsdlLocation) {
        this.wsdlLocation = wsdlLocation;
    }

    public String getWsdl() {
        return wsdlLocation;
    }
}
