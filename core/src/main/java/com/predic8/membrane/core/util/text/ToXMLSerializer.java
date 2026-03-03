/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.text;

import org.w3c.dom.*;

import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

import static javax.xml.transform.OutputKeys.*;
import static javax.xml.transform.TransformerFactory.*;

public class ToXMLSerializer {

    public static String toXML(Object o) {
        return switch (o) {
            case null -> "";
            case String s -> s; // Already XML (string)
            case Node node -> nodeToString(node);
            case NodeList nl -> nodeListToString(nl);
            default -> String.valueOf(o);
        };
    }

    private static String nodeListToString(org.w3c.dom.NodeList nl) {
        var sb = new StringBuilder();
        for (int i = 0; i < nl.getLength(); i++) {
            var n = nl.item(i);
            if (n == null) continue;
            sb.append(nodeToString(n));
        }
        return sb.toString();
    }

    private static String nodeToString(org.w3c.dom.Node node) {
        try {
            var t = newInstance().newTransformer();
            t.setOutputProperty(OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(INDENT, "no");

            var sw = new StringWriter();
            t.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            // keep consistent with your JSON helper behavior: do not throw from serializer
            return String.valueOf(node);
        }
    }
}
