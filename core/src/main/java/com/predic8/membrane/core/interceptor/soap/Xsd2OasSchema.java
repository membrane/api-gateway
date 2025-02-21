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

import io.swagger.v3.oas.models.media.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Map.entry;
import static java.util.Map.ofEntries;

public class Xsd2OasSchema {

    private static final Map<String, Supplier<Schema<?>>> TYPE_MAPPINGS = ofEntries(
            Map.entry("string", StringSchema::new),
            Map.entry("integer", IntegerSchema::new),
            Map.entry("int", IntegerSchema::new),
            Map.entry("long", IntegerSchema::new),
            Map.entry("decimal", NumberSchema::new),
            Map.entry("float", NumberSchema::new),
            Map.entry("double", NumberSchema::new),
            Map.entry("boolean", BooleanSchema::new),
            Map.entry("date", StringSchema::new),
            Map.entry("dateTime", StringSchema::new),
            Map.entry("time", StringSchema::new)
    );

    private final DocumentBuilder documentBuilder;

    public Xsd2OasSchema() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        documentBuilder = factory.newDocumentBuilder();
    }

    public Schema<?> convert(InputStream xsdStream) throws Exception {
        Element root = documentBuilder.parse(xsdStream).getDocumentElement();
        String schemaPrefix = root.getPrefix() != null ? root.getPrefix() + ":" : "";

        if ("element".equals(root.getLocalName())) {
            return processElement(root, schemaPrefix);
        }

        NodeList elements = root.getElementsByTagNameNS("*", "element");
        if (elements.getLength() > 0) {
            return processElement((Element) elements.item(0), schemaPrefix);
        }
        return new ObjectSchema();
    }

    private Schema<?> processElement(Element element, String schemaPrefix) {
        String type = element.getAttribute("type");
        String name = element.getAttribute("name");
        Schema<?> schema;

        if (!type.isBlank()) {
            schema = createSchemaFromType(type);
        } else {
            Element complexType = getFirstChildByTagNameNS(element, "complexType");
            schema = (complexType != null) ? processComplexType(complexType, schemaPrefix) : new ObjectSchema();
        }

        if (!name.isBlank()) {
            var xml = new XML();
            xml.setName(name);
            if (element.getParentNode().getNodeName().equals(schemaPrefix + "schema")) {
                xml.setPrefix(schemaPrefix.replace(":", ""));
            }
            schema.setXml(xml);
        }

        return schema;
    }

    private Schema<?> processComplexType(Element complexType, String schemaPrefix) {
        Element sequence = getFirstChildByTagNameNS(complexType, "sequence");
        if (sequence != null) {
            Map<String, Schema> properties = new HashMap<>();
            NodeList elements = sequence.getElementsByTagNameNS("*", "element");

            for (int i = 0, n = elements.getLength(); i < n; i++) {
                Element element = (Element) elements.item(i);
                String name = element.getAttribute("name");

                Schema<?> property = processElement(element, schemaPrefix);
                if ("unbounded".equals(element.getAttribute("maxOccurs"))) {
                    properties.put(name, new ArraySchema() {{
                        setItems(property);
                        setXml(new XML() {{
                            setName(name);
                            setWrapped(true);
                        }});
                    }});
                } else {
                    properties.put(name, property);
                }
            }
            return new ObjectSchema() {{
                setProperties(properties);
            }};
        }
        return new ObjectSchema();
    }

    private Schema<?> createSchemaFromType(String xsdType) {
        String baseType = xsdType.contains(":") ? xsdType.split(":", 2)[1] : xsdType;
        try {
            Schema<?> schema = TYPE_MAPPINGS.getOrDefault(baseType, StringSchema::new).get();
            if (schema instanceof StringSchema && ("date".equals(baseType)
                    || "dateTime".equals(baseType) || "time".equals(baseType))) {
                schema.setFormat(baseType);
            }
            return schema;
        } catch (Exception e) {
            return new StringSchema();
        }
    }

    private Element getFirstChildByTagNameNS(Element parent, String localName) {
        NodeList children = parent.getElementsByTagNameNS("*", localName);
        return children.getLength() > 0 ? (Element) children.item(0) : null;
    }
}