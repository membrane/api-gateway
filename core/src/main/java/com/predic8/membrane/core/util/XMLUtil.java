package com.predic8.membrane.core.util;

import org.w3c.dom.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

import static javax.xml.transform.OutputKeys.INDENT;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;

public class XMLUtil {

    // TODO 2. param boolean indent
    // Write Test
    public static String xml2string(Document doc) throws TransformerException {
        TransformerFactory tfFactory = TransformerFactory.newInstance(); // Comment ThreadSafe? with URL
        Transformer tf = tfFactory.newTransformer();
        tf.setOutputProperty(OMIT_XML_DECLARATION, "yes");

        tf.setOutputProperty(INDENT, "yes");
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
