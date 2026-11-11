/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.core.util.xml.schema.UndeclaredElementException;
import com.predic8.membrane.core.util.xml.schema.Xml2JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.Schema;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import java.io.StringReader;
import java.util.Map;

import static com.predic8.membrane.annot.Constants.SOAP11_NS;
import static com.predic8.membrane.annot.Constants.SOAP12_NS;
import static com.predic8.membrane.core.util.xml.schema.XsdDomUtil.*;
import static com.predic8.membrane.core.util.xml.parser.HardenedXmlParser.getInstance;

/**
 * Transforms SOAP XML response to JSON: finds the Body's element, or its Fault, and maps it with
 * {@link Xml2JsonMapper}, which describes how elements, attributes and values become JSON.
 */
public class Soap2JsonTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Maps the Body's element; the envelope and the Fault are handled here. */
    private final Xml2JsonMapper mapper;

    /**
     * Picks the schema for the element the SOAP Body carries, once the message has been parsed and
     * that element is known. A caller that already knows which message it converts ignores the name.
     */
    @FunctionalInterface
    interface BodySchema {
        /**
         * @param bodyElement the namespace and local name of the Body's first child element
         * @return the schema to type that element with; {@code null} converts it untyped
         * @throws UndeclaredElementException if the caller has no schema for that element and
         *                                        refuses to convert it untyped
         */
        Schema<?> forElement(QName bodyElement) throws UndeclaredElementException;
    }

    /** Pass an empty map where there are no schemas to resolve against. */
    public Soap2JsonTransformer(Map<String, Schema<?>> components) {
        this.mapper = new Xml2JsonMapper(components);
    }

    /**
     * Transforms the SOAP response to JSON.
     * <p>
     * {@code responseSchema} produces properly typed values (numbers, booleans) rather than always
     * strings; {@code faultDetailSchema} types the content of a SOAP {@code <detail>} element the
     * same way, its properties being the operation's declared faults keyed by fault element local
     * name. Either may be {@code null}, which converts that part structurally with every scalar as
     * a string — the only thing possible for untyped or {@code xsd:any} content.
     */
    public String transform(String soapXml, Schema<?> responseSchema, Schema<?> faultDetailSchema) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
                toJson(new InputSource(new StringReader(soapXml)), ignored -> responseSchema, faultDetailSchema));
    }

    /**
     * Transforms a SOAP document to UTF-8 JSON. The source decides the input encoding: with
     * {@link InputSource#setEncoding} it is fixed, otherwise the XML parser detects it from the
     * byte order mark or the XML declaration. The caller closes the source's stream.
     */
    public byte[] transform(InputSource soapXml, Schema<?> responseSchema, Schema<?> faultDetailSchema) throws Exception {
        return transform(soapXml, ignored -> responseSchema, faultDetailSchema);
    }

    /**
     * Transforms a SOAP document to UTF-8 JSON, typing the Body's element with the schema
     * {@code bodySchema} picks for it. A Fault is not looked up: {@code faultDetailSchema} types its
     * detail. Encoding and stream handling as in {@link #transform(InputSource, Schema, Schema)}.
     */
    byte[] transform(InputSource soapXml, BodySchema bodySchema, Schema<?> faultDetailSchema) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                toJson(soapXml, bodySchema, faultDetailSchema));
    }

    private Object toJson(InputSource soapXml, BodySchema bodySchema, Schema<?> faultDetailSchema) throws Exception {
        var doc = getInstance().parse(soapXml);

        Element body = getSoapBody(doc);
        if (body == null) {
            throw new IllegalArgumentException("No SOAP Body found in response");
        }

        Element responseElement = getFirstChildElement(body);
        if (responseElement == null) {
            throw new IllegalArgumentException("No response element found in SOAP Body");
        }

        if ("Fault".equals(responseElement.getLocalName())) {
            throw buildFaultException(responseElement, faultDetailSchema);
        }

        final var responseSchema = bodySchema.forElement(
                new QName(responseElement.getNamespaceURI(), responseElement.getLocalName()));
        return mapper.toJson(responseElement, responseSchema);
    }

    private static Element getSoapBody(Document doc) {
        Element body = bodyInNamespace(doc, SOAP11_NS);
        return body != null ? body : bodyInNamespace(doc, SOAP12_NS);
    }

    private static Element bodyInNamespace(Document doc, String soapNamespace) {
        NodeList bodies = doc.getElementsByTagNameNS(soapNamespace, "Body");
        return bodies.getLength() > 0 ? (Element) bodies.item(0) : null;
    }

    private static Element getFirstChildElement(Element parent) {
        var children = childElements(parent);
        return children.isEmpty() ? null : children.getFirst();
    }

    private SoapFaultException buildFaultException(Element fault, Schema<?> faultDetailSchema) {
        String ns = fault.getNamespaceURI();
        if (SOAP12_NS.equals(ns)) {
            return extractSoap12Fault(fault, faultDetailSchema);
        }
        return extractSoap11Fault(fault, faultDetailSchema);
    }

    private SoapFaultException extractSoap11Fault(Element fault, Schema<?> faultDetailSchema) {
        return new SoapFaultException(
                childText(fault, "faultcode"),
                childText(fault, "faultstring"),
                detailMap(childElement(fault, "detail"), faultDetailSchema)
        );
    }

    private SoapFaultException extractSoap12Fault(Element fault, Schema<?> faultDetailSchema) {
        Element codeEl = childElement(fault, "Code");
        Element reasonEl = childElement(fault, "Reason");
        return new SoapFaultException(
                codeEl != null ? childText(codeEl, "Value") : "",
                reasonEl != null ? childText(reasonEl, "Text") : "",
                detailMap(childElement(fault, "Detail"), faultDetailSchema)
        );
    }

    /**
     * The SOAP {@code <detail>} content as a map keyed by the local name of each fault element it
     * wraps, or null if the fault carries no detail. {@code faultDetailSchema} types the values;
     * without it, every scalar comes out as a string.
     */
    private Map<String, Object> detailMap(Element detailElement, Schema<?> faultDetailSchema) {
        return detailElement != null ? mapper.elementToMap(detailElement, faultDetailSchema) : null;
    }

    private static String childText(Element parent, String localName) {
        Element child = childElement(parent, localName);
        return child != null ? child.getTextContent().trim() : "";
    }

    private static Element childElement(Element parent, String localName) {
        for (Element el : childElements(parent)) {
            if (localName.equals(el.getLocalName())) return el;
        }
        return null;
    }
}
