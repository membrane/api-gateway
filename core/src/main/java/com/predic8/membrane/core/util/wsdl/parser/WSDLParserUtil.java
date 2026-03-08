package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import javax.xml.namespace.*;
import javax.xml.parsers.*;
import java.io.*;

public class WSDLParserUtil {

    public static Element parse(InputStream is) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(is).getDocumentElement();
    }


    public static QName resolveQName(String value, Node context) {

        if (!value.contains(":"))
            return new QName(context.lookupNamespaceURI(null), value);

        String[] p = value.split(":");
        String prefix = p[0];
        String local = p[1];

        return new QName(context.lookupNamespaceURI(prefix), local);
    }
}
