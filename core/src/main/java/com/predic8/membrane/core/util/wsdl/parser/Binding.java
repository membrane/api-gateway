package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.util.wsdl.parser.Definitions.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.DOCUMENT;
import static org.w3c.dom.Node.*;

public class Binding extends WSDLElement {

    SOAPVersion soapVersion;

    public enum Style {
        RPC, DOCUMENT;

        public static Style fromString(String style) {
            return switch (style) {
                case "rpc" -> RPC;
                default -> DOCUMENT;
            };
        }
    }

    private WSDLParserContext ctx;

    private Style style;
    private List<BindingOperation> operations;
    private PortType portType;

    public Binding(WSDLParserContext ctx, Node node) {
        super(node);
        this.ctx = ctx;
        operations = getBindingOperations(node);
        portType = getPortType(node);
    }

    public Style getStyle() {
        return style;
    }

    public List<BindingOperation> getOperations() {
        return operations;
    }

    public PortType getPortType() {
        return portType;
    }

    private List<BindingOperation> getBindingOperations(Node node) {
        var result = new ArrayList<BindingOperation>();
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            if ((child.getNodeType() == ELEMENT_NODE)
                && "operation".equals(child.getLocalName())
                && WSDL11_NS.equals(child.getNamespaceURI())) {
                result.add(new BindingOperation(ctx, child));
            }

            if (child.getNodeType() == ELEMENT_NODE
                && "binding".equals(child.getLocalName())) {
                style = getStyle(child);
                ctx.style(style);
                if (child.getNamespaceURI().equals(WSDL_SOAP11_NS)) {
                    soapVersion = SOAPVersion.SOAP_11;
                } else if (child.getNamespaceURI().equals(WSDL_SOAP12_NS)) {
                    soapVersion = SOAPVersion.SOAP_12;
                }
                ctx.getDefinitions().addSoapVersion(soapVersion);

            }
        }
        return result;
    }

    private static Style getStyle(Node child) {
        var node = child.getAttributes().getNamedItem("style");
        if (node == null) {
            return DOCUMENT;
        }
        return Style.fromString(node.getNodeValue());
    }

    private PortType getPortType(Node node) {
        if (!(node instanceof Element bindingElement)) {
            return null;
        }

        var type = bindingElement.getAttribute("type");
        if (type == null || type.isEmpty()) {
            return null;
        }

        var portTypeQName = WSDLParserUtil.resolveQName(type, bindingElement);

        Element definitions = bindingElement.getOwnerDocument().getDocumentElement();
        NodeList portTypes = definitions.getElementsByTagNameNS(WSDL11_NS, "portType");

        for (int i = 0; i < portTypes.getLength(); i++) {
            Element portType = (Element) portTypes.item(i);

            if (portTypeQName.getLocalPart().equals(portType.getAttribute("name"))) {
                return new PortType(ctx, portType);
            }
        }

        return null;
    }

}
