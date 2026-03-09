package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;

public class Message extends WSDLElement {

    private final List<Part> parts = new ArrayList<>();

    public Message(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        this.parts.add(getPart(node));
        ctx.getDefinitions().messages.add(this);
    }

    /**
     * Document style only uses one part.
     * @return
     */
    public Part getPart() {
        return parts.getFirst();
    }

    public List<Part> getParts() {
        return parts;
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
        return "Message [parts=" + parts + "]";
    }
}
