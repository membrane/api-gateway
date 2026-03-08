package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

public class Address extends WSDLElement {

    private final String location;

    public Address(WSDLParserContext ctx,Node node) {
        super(node);
        location = getLocation(node);
    }

    public String getLocation() {
        return location;
    }

    public String getLocation(Node node) {
        if (node instanceof Element e) {
            return e.getAttribute("location");
        }
        return null;
    }
}
