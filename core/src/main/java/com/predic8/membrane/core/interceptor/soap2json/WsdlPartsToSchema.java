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

package com.predic8.membrane.core.interceptor.soap2json;

import com.predic8.membrane.core.util.xml.schema.XsdToSchema;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.wsdl.parser.Part;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.core.util.xml.schema.XsdDomUtil.qualifiedKey;

/**
 * Converts the parts of WSDL messages, and the faults of an operation, to OpenAPI schemas, on top
 * of the XSD conversion of {@link XsdToSchema} over the schemas of the WSDL's import graph.
 */
public class WsdlPartsToSchema extends XsdToSchema {

    public WsdlPartsToSchema(Definitions definitions, Set<String> reservedComponentNames) {
        super(definitions.getSchemasByNamespace(), reservedComponentNames);
    }

    /**
     * Converts the parts of the first message in the list to an OpenAPI schema.
     * Returns an empty ObjectSchema if the list is empty or has no usable parts.
     */
    public Schema<?> convertMessageParts(List<Message> messages) {
        if (messages.isEmpty()) return new ObjectSchema();
        return convertParts(messages.getFirst().getParts());
    }

    /**
     * The schema of a SOAP {@code <detail>} element carrying one of the operation's declared faults:
     * an object with one optional property per fault, keyed by the name the fault element appears
     * under in the detail. Only one property is ever present in a given response, which is why the
     * published OpenAPI document turns these properties into a {@code oneOf}.
     * <p>
     * Returns an empty ObjectSchema when the operation declares no faults.
     */
    public Schema<?> convertFaultDetail(List<Operation.Fault> faults) {
        var schema = new ObjectSchema();
        faultDetailKeys(faults).forEach((key, parts) -> schema.addProperty(key, convertParts(parts)));
        return schema;
    }

    /**
     * The parts of each fault that carries content, by the key that content appears under inside
     * the detail element: the local name of its wrapping XSD element, or the part name for
     * RPC-style parts that only name a type. A fault declared more than once has one key.
     * <p>
     * Faults whose elements share a local name across namespaces are each keyed by their qualified
     * name instead ({@link com.predic8.membrane.core.util.xml.schema.XsdDomUtil#qualifiedKey}), so
     * that no fault's schema is lost; the XML to JSON mapping looks such a key up first. An element
     * without namespace keeps the plain local name, the key its unqualified detail element is
     * looked up under.
     */
    public static Map<String, List<Part>> faultDetailKeys(List<Operation.Fault> faults) {
        final var withContent = faults.stream()
                .map(fault -> fault.getMessage().getParts())
                .filter(parts -> !parts.isEmpty())
                .toList();
        final var elementsByLocalName = new HashMap<String, Set<QName>>();
        for (final var parts : withContent) {
            final var element = parts.getFirst().getElementQName();
            if (element != null) elementsByLocalName.computeIfAbsent(element.getLocalPart(), name -> new HashSet<>()).add(element);
        }
        final var keys = new LinkedHashMap<String, List<Part>>();
        for (final var parts : withContent) {
            keys.putIfAbsent(faultDetailKey(parts.getFirst(), elementsByLocalName), parts);
        }
        return keys;
    }

    private static String faultDetailKey(Part part, Map<String, Set<QName>> elementsByLocalName) {
        final var element = part.getElementQName();
        if (element == null) return part.getName();
        final var shared = elementsByLocalName.get(element.getLocalPart()).size() > 1;
        if (shared && !element.getNamespaceURI().isEmpty()) {
            return qualifiedKey(element.getNamespaceURI(), element.getLocalPart());
        }
        return element.getLocalPart();
    }

    /**
     * Converts a list of WSDL message parts to an OpenAPI schema. A single part with a wrapping
     * XSD element (document/literal wrapped style) is unwrapped to that element's own schema.
     * Any other case — RPC-style parts (type=), or multiple parts (bare style) — is represented
     * as an object with one property per part, keyed by the part's name.
     */
    public Schema<?> convertParts(List<Part> parts) {
        if (parts.isEmpty()) return new ObjectSchema();
        if (parts.size() == 1 && parts.getFirst().getElementQName() != null) {
            return convert(parts.getFirst().getElementQName());
        }
        var schema = new ObjectSchema();
        for (var part : parts) {
            QName elementQName = part.getElementQName();
            schema.addProperty(part.getName(),
                    elementQName != null ? convert(elementQName) : convertType(part.getTypeQName()));
        }
        return schema;
    }
}
