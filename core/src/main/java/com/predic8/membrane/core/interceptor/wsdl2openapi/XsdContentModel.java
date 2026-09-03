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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;

/**
 * The content model an XSD particle declares, as an ordered tree: every {@code <xsd:element>} in
 * the order an instance must present them, with {@code xsd:group} references expanded in place and
 * reference cycles cut.
 *
 * <p>This is the one traversal of an XSD content model in this package. Both directions of the
 * conversion need the same walk but do different things with it: {@link XsdToSchema} builds the
 * published schema and has to keep an {@code xsd:choice} intact, because a choice constrains which
 * alternatives may appear together, while {@link Json2SoapTransformer} only needs the field order
 * and flattens a choice like any other particle. Producing the tree once and letting each side walk
 * it keeps that difference the only difference — a particle form handled here is handled for both.
 *
 * <p>The tree deliberately stops at element declarations: what an element's <em>type</em> contains
 * is resolved by the caller, which is where the differing type-recursion rules live.
 */
class XsdContentModel {

    private static final Logger log = LoggerFactory.getLogger(XsdContentModel.class);

    /**
     * One node of a content model. Each carries the schema document it was found in, because a
     * group's fields follow the {@code elementFormDefault} and {@code targetNamespace} of the
     * document declaring the group rather than the one referring to it.
     */
    sealed interface ContentNode {
        Element declaration();

        Element schemaRoot();
    }

    /** An {@code <xsd:element>} declaration — a field of the content model. */
    record Field(Element declaration, Element schemaRoot) implements ContentNode {}

    /**
     * An {@code xsd:sequence}, {@code xsd:all} or {@code xsd:choice} and the nodes it holds. A
     * group reference yields the container the referenced group declares, so a group leaves no
     * trace in the tree beyond the nodes it contributed.
     */
    record Container(List<ContentNode> children, Element declaration, Element schemaRoot) implements ContentNode {
        boolean isChoice() {
            return "choice".equals(declaration.getLocalName());
        }
    }

    private final Map<String, List<Element>> schemasByNamespace;

    XsdContentModel(Map<String, List<Element>> schemasByNamespace) {
        this.schemasByNamespace = schemasByNamespace;
    }

    /**
     * The content model {@code particle} declares, or {@code null} where it declares none — an
     * {@code xsd:group} reference that names no reachable group, one that is already being
     * expanded, or a group whose definition carries no particle.
     *
     * @param visiting the group definitions currently being expanded; the caller owns it, so a
     *                 cycle is cut across the whole traversal it belongs to rather than only within
     *                 this particle
     */
    ContentNode nodeOf(Element particle, Element schemaRoot, Set<Element> visiting) {
        if ("group".equals(particle.getLocalName())) {
            return groupNode(particle, schemaRoot, visiting);
        }
        return new Container(childNodes(particle, schemaRoot, visiting), particle, schemaRoot);
    }

    private List<ContentNode> childNodes(Element container, Element schemaRoot, Set<Element> visiting) {
        var nodes = new ArrayList<ContentNode>();
        for (Element el : xsdChildren(container)) {
            switch (el.getLocalName()) {
                case "element" -> nodes.add(new Field(el, schemaRoot));
                case "sequence", "all", "choice", "group" -> {
                    ContentNode node = nodeOf(el, schemaRoot, visiting);
                    if (node != null) nodes.add(node);
                }
                // xsd:any: a wildcard declares no field, so it contributes no node
            }
        }
        return List.copyOf(nodes);
    }

    /** Expands an {@code <xsd:group ref=.../>} into the container its definition declares. */
    private ContentNode groupNode(Element groupRef, Element schemaRoot, Set<Element> visiting) {
        var group = resolveGroupRef(groupRef, schemaRoot, schemasByNamespace);
        if (group.isEmpty()) {
            log.debug("xsd:group ref='{}' could not be resolved, skipping", groupRef.getAttribute("ref"));
            return null;
        }
        Element definition = group.get().definition();
        if (!visiting.add(definition)) {
            log.debug("Recursive reference to group '{}', skipping", definition.getAttribute("name"));
            return null;
        }
        try {
            Element particle = groupParticle(definition);
            return particle == null ? null : nodeOf(particle, group.get().schemaRoot(), visiting);
        } finally {
            visiting.remove(definition);
        }
    }

    /** Every field of {@code node}'s subtree, in declaration order, with particle structure dropped. */
    static List<Field> fieldsOf(ContentNode node) {
        var fields = new ArrayList<Field>();
        collectFields(node, fields);
        return fields;
    }

    private static void collectFields(ContentNode node, List<Field> fields) {
        switch (node) {
            case Field field -> fields.add(field);
            case Container container -> container.children().forEach(child -> collectFields(child, fields));
        }
    }
}
