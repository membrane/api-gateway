/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot;

import org.w3c.dom.*;

import javax.lang.model.element.*;

import static org.w3c.dom.Node.*;

public class AnnotUtils {

    public static final String QUOTE = "\"";

    public static String javaify(String s) {
        StringBuilder sb = new StringBuilder(s);
        sb.replace(0, 1, "" + Character.toUpperCase(s.charAt(0)));
        return sb.toString();
    }

    public static String dejavaify(String s) {
        StringBuilder sb = new StringBuilder(s);
        sb.replace(0, 1, "" + Character.toLowerCase(s.charAt(0)));
        return sb.toString();
    }

    public static String getRuntimeClassName(TypeElement element) {
        if (element.getEnclosingElement() instanceof TypeElement) {
            return getRuntimeClassName((TypeElement) element.getEnclosingElement()) + "$" + element.getSimpleName();
        }
        return element.getQualifiedName().toString();
    }

    /**
     * Takes a XML element an converts its child content into a string. In contrast to
     * getTextcontent it renders all the elements and their attributes. It is used to get
     * rid of CDATA sections in proxies.xml files.
     * @param element DOM node
     * @return XML String
     */
    public static String getContentAsString(Node element) {
        StringBuilder sb = new StringBuilder();
        NodeList nl = element.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            switch (n.getNodeType()) {
                case ELEMENT_NODE: {
                    sb.append(elementToString(n));
                    break;
                }
                case TEXT_NODE:
                    sb.append(n.getNodeValue());
                    break;
                case CDATA_SECTION_NODE:
                    sb.append(n.getTextContent());
                    break;
            }
        }
        return sb.toString();
    }

    private static String elementToString(Node n) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(n.getNodeName());
        sb.append(attrToString(n));
        sb.append(">");
        sb.append(getContentAsString(n));
        sb.append("</").append(n.getNodeName()).append(">");
        return sb.toString();
    }

    private static String attrToString(Node n) {
        if (!n.hasAttributes())
            return "";

        StringBuilder sb = new StringBuilder();

        NamedNodeMap nm = n.getAttributes();
        for (int j = 0; j < nm.getLength(); j++) {
            Node a = nm.item(j);
            sb.append(" ").append(a.getNodeName()).append("=");
            sb.append(QUOTE);
            sb.append(a.getNodeValue());
            sb.append(QUOTE);
        }

        return sb.toString();
    }

}
