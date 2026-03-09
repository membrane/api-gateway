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

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.schema.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static org.w3c.dom.Node.*;

/**
 * WSDL elements register themselves via WSDLParserContext. This is more convenient, e.g. binding is not
 * directly created.
 */
public class Definitions {

    private static final Logger log = LoggerFactory.getLogger(Definitions.class);

    public enum SOAPVersion {
        SOAP_11, SOAP_12
    }

    private List<Schema> schemas = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private List<PortType> portTypes = new ArrayList<>();
    private List<Binding> bindings = new ArrayList<>();
    private List<Service> services = new ArrayList<>();

    private String targetNamespace;
    private String name;

    Set<SOAPVersion> soapVersions = new HashSet<>();

    private Definitions() {
    }

    public void parse(WSDLParserContext ctx, Element element) {
        targetNamespace = element.getAttribute("targetNamespace");
        name = getName(element);

        for (var schemaElement : getSchemaElements(element)) {
            schemas.add(new Schema(ctx, schemaElement));
        }

        importEmbeddedSchemas();

        for (var e : getDirectChildElements(element, "service")) {
            // Registers itself via WSDLParserContext
            new Service(ctx, e);
        }

        // Abstract WSDL without binding and service elements
        if (services.isEmpty()) {
            for (var e : getDirectChildElements(element, "portType")) {
                // Registers itself via WSDLParserContext
                new PortType(ctx, e);
            }
        }
    }

    private static @Nullable String getName(Element element) {
        var name = element.getAttribute("name");
        if (name.isEmpty())
            return null;
        return name;
    }

    private void importEmbeddedSchemas() {
        for (var schema : schemas) {
            for (var i : schema.getImports()) {
                if (i.getSchemaLocation() == null || i.getSchemaLocation().isEmpty()) {
                    var importedSchema = getEmbeddedSchema(i.getNamespace());
                    log.debug("Importing embedded schema with namespace: {}", i.getNamespace());
                    i.setSchema(importedSchema);
                }
            }
        }
    }

    private @Nullable Schema getEmbeddedSchema(String namespace) {
        return schemas.stream().filter(s -> Objects.equals(s.getTargetNamespace(), namespace))
                .findFirst().orElse(null);
    }

    public static Definitions parse(Resolver resolver, String location) throws Exception {
        log.debug("Parsing WSDL from {}", location);
        var defs = new Definitions();
        try (var is = resolver.resolve(location)) {
            defs.parse(new WSDLParserContext(defs, resolver, location, new ArrayList<>()), WSDLParserUtil.parse(is));
        }
        return defs;
    }

    public List<Schema> getSchemas() {
        return schemas;
    }

    public List<Element> getSchemaElements() {
        return schemas.stream().map(Schema::getSchemaElement).toList();
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<PortType> getPortTypes() {
        return portTypes;
    }

    public List<Binding> getBindings() {
        return bindings;
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

    public String getName() {
        return name;
    }

    public Set<SOAPVersion> getSoapVersions() {
        return soapVersions;
    }

    public void addSoapVersion(SOAPVersion soapVersion) {
        soapVersions.add(soapVersion);
    }

    private List<Element> getSchemaElements(Element wsdl) {
        var schemas = new ArrayList<Element>();

        var typesList = wsdl.getElementsByTagNameNS(WSDL11_NS, "types");
        for (int i = 0; i < typesList.getLength(); i++) {
            var types = (Element) typesList.item(i);
            var children = types.getChildNodes();

            for (int j = 0; j < children.getLength(); j++) {
                var child = children.item(j);

                if ((child.getNodeType() == ELEMENT_NODE)
                    && "schema".equals(child.getLocalName())
                    && XSD_NS.equals(child.getNamespaceURI())) {
                    schemas.add((Element) child);
                }
            }
        }
        return schemas;
    }

    private static List<Element> getDirectChildElements(Element parent, String name) {
        var result = new ArrayList<Element>();
        var children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var node = children.item(i);
            if (node.getNodeType() == ELEMENT_NODE) {
                var element = (Element) node;
                if (WSDL11_NS.equals(element.getNamespaceURI()) &&
                    name.equals(element.getLocalName())) {
                    result.add(element);
                }
            }
        }

        return result;
    }
}