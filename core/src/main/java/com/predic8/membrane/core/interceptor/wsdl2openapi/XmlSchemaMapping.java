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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.schema.Schema;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import java.util.*;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.qualifiedKey;
import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.xsdChildren;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static com.predic8.membrane.core.util.xml.parser.HardenedXmlParser.getInstance;

/**
 * Converts XML documents to JSON and back, typed by the global element declarations of an XML
 * Schema — the mapping soap2Json and json2Soap apply to a SOAP Body, here applied to a whole
 * document.
 *
 * <p>The JSON is an object with a single property: the root element, keyed by its local name, or
 * by {@code {namespace}local} where the schemas declare that local name globally in more than one
 * namespace. Its value is the element's content as {@link Xml2JsonMapper} maps it.
 *
 * <p>Built completely in {@link #load} and immutable afterwards: the schemas are a Xerces DOM,
 * which concurrent reads corrupt, so requests never touch them.
 */
public final class XmlSchemaMapping {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A global element: its JSON key, the schema that types it, and the transformer that writes it. */
    private record Root(QName element, String key, io.swagger.v3.oas.models.media.Schema<?> schema,
                        Json2XmlTransformer writer) {}

    /** The XML Schema as configured, for messages. */
    private final String xsd;
    private final Map<QName, Root> byElement;
    private final Map<String, Root> byKey;
    private final Xml2JsonMapper mapper;

    private XmlSchemaMapping(String xsd, Map<QName, Root> byElement, Map<String, Root> byKey,
                             Map<String, io.swagger.v3.oas.models.media.Schema<?>> components) {
        this.xsd = xsd;
        this.byElement = byElement;
        this.byKey = byKey;
        this.mapper = new Xml2JsonMapper(components);
    }

    /**
     * Loads the XML Schema, with everything it imports and includes, resolving {@code xsd} against
     * the location of the configuration that declares the interceptor.
     *
     * @throws ConfigurationException if the schema cannot be read or declares no global element
     */
    public static XmlSchemaMapping load(Router router, String baseLocation, String xsd) {
        final Map<String, List<Element>> schemasByNamespace;
        try {
            final var location = combine(router.getConfiguration().getUriFactory(), baseLocation, xsd);
            schemasByNamespace = Schema.schemasByNamespace(List.of(Schema.parse(router.getResolverMap(), location)));
        } catch (Exception e) {
            throw new ConfigurationException("Cannot parse XML Schema '%s': %s".formatted(xsd, e.getMessage()), e);
        }
        return of(xsd, schemasByNamespace);
    }

    /**
     * Converts every global element declaration of the schemas.
     *
     * @throws ConfigurationException if the schemas declare no global element
     */
    static XmlSchemaMapping of(String xsd, Map<String, List<Element>> schemasByNamespace) {
        final var elements = globalElements(schemasByNamespace);
        if (elements.isEmpty()) {
            throw new ConfigurationException("XML Schema '%s' declares no global element, so there is no document it could convert.".formatted(xsd));
        }

        final var localNameCount = new HashMap<String, Integer>();
        elements.forEach(element -> localNameCount.merge(element.getLocalPart(), 1, Integer::sum));

        final var xsdToSchema = new XsdToSchema(schemasByNamespace, Set.of());
        final var byElement = new LinkedHashMap<QName, Root>();
        final var byKey = new LinkedHashMap<String, Root>();
        for (final var element : elements) {
            final var key = localNameCount.get(element.getLocalPart()) > 1
                    ? qualifiedKey(element.getNamespaceURI(), element.getLocalPart())
                    : element.getLocalPart();
            final var root = new Root(element, key, xsdToSchema.convert(element),
                    new Json2XmlTransformer(element, schemasByNamespace));
            byElement.put(element, root);
            byKey.put(key, root);
        }
        // Copied only now: every conversion may have added components.
        return new XmlSchemaMapping(xsd, Map.copyOf(byElement), Map.copyOf(byKey), Map.copyOf(xsdToSchema.getComponents()));
    }

    /** The global {@code xsd:element} declarations of every schema document, in document order. */
    private static List<QName> globalElements(Map<String, List<Element>> schemasByNamespace) {
        final var elements = new LinkedHashSet<QName>();
        schemasByNamespace.forEach((namespace, roots) -> {
            for (final var root : roots) {
                for (final var child : xsdChildren(root)) {
                    final var name = child.getAttribute("name");
                    if ("element".equals(child.getLocalName()) && !name.isEmpty()) {
                        elements.add(new QName(namespace, name));
                    }
                }
            }
        });
        return List.copyOf(elements);
    }

    /**
     * Converts an XML document to UTF-8 JSON. The source decides the input encoding: with
     * {@link InputSource#setEncoding} it is fixed, otherwise the XML parser detects it from the
     * byte order mark or the XML declaration. The caller closes the source's stream.
     *
     * @throws UndeclaredElementException if the schemas do not declare the document element globally
     */
    public byte[] toJson(InputSource xml) throws Exception {
        final var documentElement = getInstance().parse(xml).getDocumentElement();
        final var element = new QName(documentElement.getNamespaceURI(), documentElement.getLocalName());
        final var root = byElement.get(element);
        if (root == null) throw undeclared(element);

        final var json = new LinkedHashMap<String, Object>();
        json.put(root.key(), mapper.toJson(documentElement, root.schema()));
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(json);
    }

    /**
     * Converts JSON to a UTF-8 XML document. The JSON must be an object with exactly one property,
     * whose key names the root element.
     *
     * @throws IllegalArgumentException   if the JSON is not such an object
     * @throws UndeclaredElementException if the key names no global element of the schemas
     * @throws org.w3c.dom.DOMException   if a key is not a valid XML name
     */
    public byte[] toXml(String json) throws Exception {
        final var node = MAPPER.readTree(json);
        if (node == null || !node.isObject() || node.size() != 1) {
            throw new IllegalArgumentException("The JSON must be an object with exactly one property, named after the root element.");
        }
        final var property = node.properties().iterator().next();
        final var root = byKey.get(property.getKey());
        if (root == null) throw undeclared(QName.valueOf(property.getKey()));
        return root.writer().transform(property.getValue());
    }

    private UndeclaredElementException undeclared(QName element) {
        return new UndeclaredElementException(element, "XML Schema '%s' as a global element".formatted(xsd));
    }

    /** The number of global elements this mapping converts, for the startup log. */
    public int size() {
        return byElement.size();
    }
}
