package com.predic8.membrane.core.util.wsdl.parser;

import com.predic8.membrane.annot.*;
import org.w3c.dom.*;

import static com.predic8.membrane.annot.Constants.WSDL11_NS;

public class Message extends WSDLElement {

    private final Part part;

    public Message(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        this.part = getPart(node);
    }

    public Part getPart() {
        return part;
    }

    private Part getPart(Node node) {
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE
                && "part".equals(child.getLocalName())
                && WSDL11_NS.equals(child.getNamespaceURI())) {
                return new Part(ctx, child);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Message [part=" + part + "]";
    }
}
