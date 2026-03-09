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

    List<Schema> schemas = new ArrayList<>();
    List<Message> messages = new ArrayList<>();
    List<PortType> portTypes = new ArrayList<>();
    List<Binding> bindings = new ArrayList<>();
    List<Service> services = new ArrayList<>();

    String targetNamespace;

    Set<SOAPVersion> soapVersions = new HashSet<>();

    private Definitions() {
    }

    public void parse(WSDLParserContext ctx, Element element) {
        targetNamespace = element.getAttribute("targetNamespace");

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
        return schemas.stream().filter(s -> s.getTargetNamespace().equals(namespace)).findFirst().orElse(null);
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