package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import javax.xml.namespace.*;

public class Part extends WSDLElement {

    private WSDLParserContext ctx;

    private QName element;
    private QName type;

    public Part(WSDLParserContext ctx, Node node) {
        super(node);
        this.ctx = ctx;
        this.element = getElementQName(node);
        this.type = getTypeQName(node);
    }


    public QName getElementQName() {
        return element;
    }

    public QName getTypeQName() {
        return type;
    }

    private QName getTypeQName(Node node) {
        if (!(node instanceof org.w3c.dom.Element partElement)) {
            return null;
        }

        String typeAttr = partElement.getAttribute("type");
        if (typeAttr.isEmpty()) {
            return null;
        }

        return WSDLParserUtil.resolveQName(typeAttr, partElement);
    }

    private QName getElementQName(Node node) {
        if (!(node instanceof Element element)) {
            return null;
        }
        var elementAttr = element.getAttribute("element");
        if (elementAttr.isEmpty()) {
            return null;
        }
        return WSDLParserUtil.resolveQName(elementAttr, element);
    }

    @Override
    public String toString() {
        return "Part [element=" + element + ", type=" + type + "]";
    }
}
