package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.schema.*;
import org.w3c.dom.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static org.w3c.dom.Node.*;

public class Definitions {

    public enum SOAPVersion {
        SOAP_11, SOAP_12
    }

    WSDLParserContext ctx;

    List<Schema> schemas = new ArrayList<>();
    List<Message> messages;
    List<PortType> portTypes = new ArrayList<>();
    List<Binding> bindings;
    List<Service> services = new ArrayList<>();

    String targetNamespace;

    Set<SOAPVersion> soapVersions = new HashSet<>();

    private Definitions() {
    }

    public Definitions(WSDLParserContext ctx) {
        this.ctx = ctx.definitions(this);
    }

    public void parse(Element element) {
        targetNamespace = element.getAttribute("targetNamespace");

        for (var schemaElement : getSchemaElements(element)) {
            schemas.add(new Schema(ctx, schemaElement));
        }

        for (var e : getElements(element, "service")) {
            new Service(ctx, e);
        }

        // Abstract WSDL without binding and service elements
        if (services.isEmpty()) {
            for (var e : getElements(element, "portType")) {
                new PortType(ctx, e);
            }
        }
    }

    public static Definitions parse(Resolver resolver, String location) throws Exception {
        var defs = new Definitions();
        defs.ctx = new WSDLParserContext(defs, resolver, location, new ArrayList<>());
        try(var is = resolver.resolve(location)) {
            defs.parse(WSDLParserUtil.parse(is));
        }
        return defs;
    }

    public void parse(InputStream is, Resolver resolver) {
        try {
            ctx = ctx.resolver(resolver);
            parse(WSDLParserUtil.parse(is));
        } catch (Exception e) {
            throw new RuntimeException("Could not parse WSDL", e);
        }
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

    public Service getService(String name) {
        return services.stream().filter(s -> s.getName().equals(name)).findFirst().orElse(null);
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

    private static List<Element> getElements(Element wsdl, String name) {
        var services = new ArrayList<Element>();
        var list = wsdl.getElementsByTagNameNS(WSDL11_NS, name);

        for (int i = 0; i < list.getLength(); i++) {
            services.add((Element) list.item(i));
        }
        return services;
    }


}