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

package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.resolver.Resolver;
import com.predic8.membrane.core.util.wsdl.parser.schema.Schema;
import com.predic8.membrane.core.util.wsdl.parser.schema.Types;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * WSDL elements register themselves via WSDLParserContext. This is more convenient, e.g. binding is not
 * directly created.
 */
public class Definitions extends WSDLElement {

    private static final Logger log = LoggerFactory.getLogger(Definitions.class);

    public enum SOAPVersion {
        SOAP_11, SOAP_12, UNKNOWN
    }

    private List<Schema> schemas = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private List<PortType> portTypes = new ArrayList<>();
    private List<Binding> bindings = new ArrayList<>();
    private List<Service> services = new ArrayList<>();

    private String targetNamespace;

    private Set<SOAPVersion> soapVersions = new HashSet<>();

    private Definitions(Resolver resolver, String location) throws Exception {
        super(new WSDLParserContext(null, resolver, location, new ArrayList<>()), read(resolver, location));
        ctx = ctx.definitions(this);
        parse();
    }

    private static Node read(Resolver resolver, String location) throws Exception {
        try (var is = resolver.resolve(location)) {
            return WSDLParserUtil.parse(is);
        }
    }

    public static Definitions parse(Resolver resolver, String location) throws Exception {
        log.debug("Parsing WSDL from {}", location);
        return new Definitions(resolver, location);
    }

    private void parse() {
        targetNamespace = getAttribute("targetNamespace");
        schemas = getSchema();

        importEmbeddedSchemas();
        messages = instantiateWSDLChildren("message", Message.class);
        portTypes = instantiateWSDLChildren("portType", PortType.class);
        bindings = instantiateWSDLChildren( "binding", Binding.class);
        services = instantiateWSDLChildren( "service", Service.class);
        soapVersions = bindings.stream().map(Binding::getSoapVersion).collect(toSet());
    }

    private @NotNull List<Schema> getSchema() {
        // If there are no types, return an empty list.
        return instantiateChild("types", Types.class)
                .map(t -> instantiateXSDElements(t.element, "schema", Schema.class))
                .orElseGet(ArrayList::new);
    }

    /**
     * Go through all embedded schemas and import all embedded schemas that are referenced there
     */
    private void importEmbeddedSchemas() {
        schemas.forEach(s -> s.getImports()
                .forEach(i -> i.importEmbeddedSchema(this)));
    }

    public Optional<Schema> getEmbeddedSchema(String namespace) {
        return schemas.stream()
                .filter(s -> Objects.equals(s.getTargetNamespace(), namespace))
                .findFirst();
    }

    public Types getTypes() {
        return instantiateChild("types", Types.class)
                .orElseThrow(() -> new WSDLParserException("No types element found in WSDL"));
    }

    public List<Schema> getSchemas() {
        return schemas;
    }

    /**
     * Builds namespace -> schema-root-element list by BFS over imports and includes.
     * Uses identity-based dedup. Schemas with a non-null targetNamespace are added to the map;
     * schemas included without their own targetNamespace are still queued for traversal so their
     * sub-imports and sub-includes are discovered.
     */
    public Map<String, List<Element>> getSchemasByNamespace() {
        var map = new LinkedHashMap<String, List<Element>>();
        var queue = new ArrayDeque<>(schemas);
        var seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.addAll(schemas);
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

    public List<Element> getSchemaElements() {
        return schemas.stream().map(Schema::getSchemaElement).toList();
    }

    public List<Message> getMessages() {
        return messages;
    }

    public Optional<Message> findMessage(String name) {
        return messages.stream().filter(m -> m.getName().equals(name)).findFirst();
    }

    public List<PortType> getPortTypes() {
        return portTypes;
    }

    public List<Binding> getBindings() {
        return bindings;
    }

    /** The bindings of that port type, in document order. */
    public List<Binding> getBindings(PortType portType) {
        return bindings.stream()
                .filter(b -> b.findPortType().filter(pt -> pt.getName().equals(portType.getName())).isPresent())
                .toList();
    }

    public List<Service> getServices() {
        return services;
    }

    public Optional<Service> getService(String name) {
        return services.stream().filter(s -> s.getName().equals(name)).findFirst();
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public Set<SOAPVersion> getSoapVersions() {
        return soapVersions;
    }
}