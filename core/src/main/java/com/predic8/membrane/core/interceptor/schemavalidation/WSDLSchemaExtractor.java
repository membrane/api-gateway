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

package com.predic8.membrane.core.interceptor.schemavalidation;

import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import java.io.*;
import java.util.*;

import static javax.xml.XMLConstants.*;

public final class WSDLSchemaExtractor {

    private static final String XML_SCHEMA_NS = "http://www.w3.org/2001/XMLSchema"; // TODO
    private static final String XMLNS_NS = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

    private WSDLSchemaExtractor() {
    }

    public static List<Document> getSchemas(Element wsdl) {
        try {
            var result = new ArrayList<Document>();
            var schemas = wsdl.getElementsByTagNameNS(XML_SCHEMA_NS, "schema");
            for (int i = 0; i < schemas.getLength(); i++) {
                result.add(extractSchema((Element) schemas.item(i),
                        getNamespaceDeclarations(wsdl)));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Could not extract embedded schemas from WSDL.", e);
        }
    }

    private static List<Attr> getNamespaceDeclarations(Element element) {
        var namespaces = new ArrayList<Attr>();
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = (Attr) attributes.item(i);
            if (XMLNS_NS.equals(attr.getNamespaceURI())) {
                namespaces.add(attr);
            }
        }
        return namespaces;
    }

    private static Document extractSchema(Element originalSchema, List<Attr> definitionNamespaces) throws Exception {
        var fac = DocumentBuilderFactory.newInstance();
   //     fac.setFeature(FEATURE_SECURE_PROCESSING, true);
        fac.setNamespaceAware(true);
        var builder = fac.newDocumentBuilder();

        var schema = builder.newDocument();
        var copiedSchema = (Element) schema.importNode(originalSchema, true);
        schema.appendChild(copiedSchema);

        addMissingNamespaceDeclarations(copiedSchema, definitionNamespaces);

        return schema;
    }

    private static void addMissingNamespaceDeclarations(Element schema, List<Attr> definitionNamespaces) {
        for (var nsDecl : definitionNamespaces) {
            if (!schema.hasAttributeNS(XMLNS_NS, getNamespaceLocalName(nsDecl))) {
                schema.setAttributeNS(XMLNS_NS, nsDecl.getName(), nsDecl.getValue());
            }
        }
    }

    private static String getNamespaceLocalName(Attr nsDecl) {
        if (XMLNS_ATTRIBUTE.equals(nsDecl.getPrefix())) {
            return nsDecl.getLocalName();
        }
        return XMLNS_ATTRIBUTE;
    }
}