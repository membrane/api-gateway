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

import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.predic8.membrane.annot.Constants.XSD_NS;

/** Static DOM helpers for XSD traversal shared across wsdl2openapi transformers. */
class XsdDomUtil {

    /** Prefix marking a JSON property that maps to an XML attribute rather than a child element. */
    static final String ATTRIBUTE_PREFIX = "@";

    /**
     * The JSON property holding an element's own text value when the element also carries
     * attributes — an XSD {@code simpleContent} type. Without attributes such an element stays a
     * plain scalar and this key is not used.
     */
    static final String VALUE_KEY = "$value";

    /** Where a schema reference points: the {@code components/schemas} section of the document. */
    static final String COMPONENTS_SCHEMAS_PREFIX = "#/components/schemas/";

    /** The component a reference names, e.g. {@code #/components/schemas/Address} -> {@code Address}. */
    static String componentName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    /** Namespace of the instance-level attributes, of which {@code xsi:nil} is used here. */
    static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    /** The attribute an instance uses to mark a nillable element as carrying no value. */
    static final String NIL_ATTRIBUTE = "nil";

    private XsdDomUtil() {}

    /** The JSON property name an XML attribute is mapped to, e.g. {@code id} -> {@code @id}. */
    static String attributeKey(String localName) {
        return ATTRIBUTE_PREFIX + localName;
    }

    /** All child elements of {@code parent}, in document order. */
    static List<Element> childElements(Element parent) {
        var result = new ArrayList<Element>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) result.add(el);
        }
        return result;
    }

    /** All child elements of {@code parent} that are in the XSD namespace, in document order. */
    static List<Element> xsdChildren(Element parent) {
        var result = new ArrayList<Element>();
        for (Element el : childElements(parent)) {
            if (XSD_NS.equals(el.getNamespaceURI())) result.add(el);
        }
        return result;
    }

    /** First xsd: child with the given local name, or null. */
    static Element findXsdChild(Element parent, String xsdLocalName) {
        for (Element el : xsdChildren(parent)) {
            if (xsdLocalName.equals(el.getLocalName())) return el;
        }
        return null;
    }

    /** First xsd: child whose local name is one of {@code xsdLocalNames}, or null. */
    static Element firstXsdChild(Element parent, String... xsdLocalNames) {
        var wanted = List.of(xsdLocalNames);
        for (Element el : xsdChildren(parent)) {
            if (wanted.contains(el.getLocalName())) return el;
        }
        return null;
    }

    /** First xsd: child with given local name and name="..." attribute, or null. */
    static Element findXsdChildWithName(Element parent, String xsdLocalName, String nameAttr) {
        for (Element el : xsdChildren(parent)) {
            if (xsdLocalName.equals(el.getLocalName()) && nameAttr.equals(el.getAttribute("name"))) return el;
        }
        return null;
    }

    /**
     * Builds namespace -> schema-root-element list by BFS over imports and includes.
     * Uses identity-based dedup. Schemas with a non-null targetNamespace are added to the map;
     * schemas included without their own targetNamespace are still queued for traversal so their
     * sub-imports and sub-includes are discovered.
     */
    static Map<String, List<Element>> buildSchemaMap(Definitions definitions) {
        var map = new LinkedHashMap<String, List<Element>>();
        var queue = new ArrayDeque<>(definitions.getSchemas());
        var seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(definitions.getSchemas());
        while (!queue.isEmpty()) {
            var schema = queue.poll();
            var ns = schema.getTargetNamespace();
            if (ns != null) {
                map.computeIfAbsent(ns, k -> new ArrayList<>()).add(schema.getSchemaElement());
            }
            for (var imp : schema.getImports()) {
                var imported = imp.getSchema();
                if (imported != null && imported.getTargetNamespace() != null && seen.add(imported)) {
                    queue.add(imported);
                }
            }
            for (var inc : schema.getIncludes()) {
                var included = inc.getSchema();
                if (included != null && seen.add(included)) {
                    queue.add(included);
                }
            }
        }
        return map;
    }

    /** Prefix from "tns:Foo" -> "tns"; returns "" if no colon. */
    static String prefix(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return colon >= 0 ? qualifiedName.substring(0, colon) : "";
    }

    /** Local name from "tns:Foo" -> "Foo"; returns full string if no colon. */
    static String localName(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return colon >= 0 ? qualifiedName.substring(colon + 1) : qualifiedName;
    }

    /**
     * Builds a Clark-notation key ({@code "{namespaceURI}localName"}) used to disambiguate
     * xsd:choice alternatives that share a local name across different namespaces.
     */
    static String qualifiedKey(String namespaceURI, String localName) {
        return "{" + namespaceURI + "}" + localName;
    }

    /**
     * Extracts the local name from a key possibly produced by {@link #qualifiedKey}; returns
     * the input unchanged if it isn't in qualified form.
     */
    static String localNameFromKey(String key) {
        if (key.startsWith("{")) {
            int close = key.indexOf('}');
            if (close >= 0) return key.substring(close + 1);
        }
        return key;
    }

    /**
     * The component name each named XSD type is published under, keyed by {@link #qualifiedKey}. A
     * type whose local name is unique keeps it; where several namespaces declare the same local name,
     * the lexicographically first namespace keeps the bare name and the others are prefixed with a
     * name derived from their namespace. Names are computed for every declared type up front, so a
     * name never depends on the order in which types are reached during conversion.
     *
     * <p>{@code reserved} names are treated as already taken, which is how a type the WSDL happens to
     * declare cannot silently overwrite a component the converter contributes itself.
     */
    static Map<String, String> buildComponentNames(Map<String, List<Element>> schemasByNamespace, Set<String> reserved) {
        var namespacesByLocalName = collectNamedTypes(schemasByNamespace);
        var taken = new HashSet<>(reserved);
        var names = new LinkedHashMap<String, String>();
        // Sorted by local name so the residual-collision suffixes below do not depend on map order.
        new TreeMap<>(namespacesByLocalName).forEach((localName, namespaces) -> {
            boolean first = true;
            for (String namespace : namespaces) {
                String candidate = sanitizeComponentName(first ? localName : nsPrefix(namespace) + "_" + localName);
                names.put(qualifiedKey(namespace, localName), uniqueName(candidate, taken));
                first = false;
            }
        });
        return names;
    }

    /** Local name -> the namespaces declaring a complexType or simpleType of that name, sorted. */
    private static Map<String, SortedSet<String>> collectNamedTypes(Map<String, List<Element>> schemasByNamespace) {
        var result = new LinkedHashMap<String, SortedSet<String>>();
        schemasByNamespace.forEach((namespace, roots) -> {
            for (Element root : roots) {
                for (Element child : xsdChildren(root)) {
                    if (!"complexType".equals(child.getLocalName()) && !"simpleType".equals(child.getLocalName())) continue;
                    String name = child.getAttribute("name");
                    // Two roots of one namespace declaring the same name collapse into one entry,
                    // matching the first-root-wins lookup in XsdToSchema.
                    if (!name.isEmpty()) result.computeIfAbsent(name, k -> new TreeSet<>()).add(namespace);
                }
            }
        });
        return result;
    }

    /** {@code candidate}, suffixed until it is not among {@code taken}; adds the result to {@code taken}. */
    private static String uniqueName(String candidate, Set<String> taken) {
        String name = candidate;
        for (int i = 2; !taken.add(name); i++) {
            name = candidate + "_" + i;
        }
        return name;
    }

    /**
     * A name usable in a {@code #/components/schemas/...} reference: everything outside
     * {@code [A-Za-z0-9._-]} becomes an underscore. An XSD name is an NCName and may contain
     * characters — Unicode letters above all — that a reference cannot carry.
     */
    static String sanitizeComponentName(String name) {
        String sanitized = name.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? "type" : sanitized;
    }

    /**
     * A short name for a namespace, used to distinguish types that share a local name: the last
     * meaningful segment of the URI, e.g. {@code http://example.com/billing} or
     * {@code urn:example:billing:v1} both yield {@code billing}. A bare version segment is skipped,
     * because a namespace that ends in one names the version rather than the domain.
     */
    static String nsPrefix(String namespace) {
        String withoutScheme = namespace.replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*:(//)?", "");
        String path = withoutScheme.split("[?#]")[0];
        var segments = Arrays.stream(path.split("[/:]")).filter(s -> !s.isEmpty()).toList();
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!segments.get(i).matches("v\\d+")) return segments.get(i);
        }
        return path;
    }

    /**
     * Resolves schema roots for the namespace identified by prefix in context of contextElement.
     * Falls back to currentSchemaRoot if the prefix or namespace is unknown.
     */
    static List<Element> resolveTargetSchemaRoots(
            String prefix, Element contextElement, Element currentSchemaRoot,
            Map<String, List<Element>> schemasByNamespace) {
        if (prefix.isEmpty()) return List.of(currentSchemaRoot);
        String nsUri = contextElement.lookupNamespaceURI(prefix);
        if (nsUri == null) return List.of(currentSchemaRoot);
        List<Element> roots = schemasByNamespace.get(nsUri);
        return (roots != null && !roots.isEmpty()) ? roots : List.of(currentSchemaRoot);
    }

    /**
     * Converts a WSDL operation name to a URL-safe kebab-case path segment.
     * Handles camelCase, PascalCase, snake_case, and mixed forms.
     * Underscores and uppercase letters both act as word separators;
     * consecutive separators are collapsed to a single dash.
     */
    static String camelToKebab(String name) {
        var result = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != '-')
                    result.append('-');
                prev = c;
            } else if (Character.isUpperCase(c)) {
                if (Character.isLowerCase(prev))
                    result.append('-');
                result.append(Character.toLowerCase(c));
                prev = c;
            } else {
                result.append(c);
                prev = c;
            }
        }
        return result.toString();
    }
}
