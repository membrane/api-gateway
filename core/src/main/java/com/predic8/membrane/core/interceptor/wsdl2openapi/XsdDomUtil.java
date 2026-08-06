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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.predic8.membrane.annot.Constants.XSD_NS;

/** Static DOM helpers for XSD traversal shared across wsdl2openapi transformers. */
class XsdDomUtil {

    private XsdDomUtil() {}

    /** First xsd: child with the given local name, or null. */
    static Element findXsdChild(Element parent, String xsdLocalName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && XSD_NS.equals(el.getNamespaceURI())
                    && xsdLocalName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    /** First xsd: child with given local name and name="..." attribute, or null. */
    static Element findXsdChildWithName(Element parent, String xsdLocalName, String nameAttr) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && XSD_NS.equals(el.getNamespaceURI())
                    && xsdLocalName.equals(el.getLocalName())
                    && nameAttr.equals(el.getAttribute("name"))) {
                return el;
            }
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
