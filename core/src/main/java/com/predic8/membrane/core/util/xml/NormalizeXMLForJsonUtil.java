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

package com.predic8.membrane.core.util.xml;

import com.fasterxml.jackson.databind.*;
import org.w3c.dom.*;

import java.util.*;

import static org.w3c.dom.Node.*;

/**
 * Utility class to normalize XML structures for JSON compatibility.
 * The class provides methods to convert XML-based objects, such as
 * Node and NodeList, into JSON-friendly structures.
 * <p>
 * This involves processing XML nodes and attributes, and representing
 * them as JSON-compatible maps, arrays, and primitive types.
 * <p>
 * This class is stateless and thread-safe, and it cannot be instantiated.
 */
public class NormalizeXMLForJsonUtil {

    private static final ObjectMapper om = new ObjectMapper();

    private NormalizeXMLForJsonUtil() {
    }

    /**
     * Normalizes an input object for JSON serialization. The method converts
     * objects such as {@link NodeList} or {@link Node} to JSON-compatible objects.
     * <p/>
     * NodeList with one item is not converted to an array!
     * <p/>
     *
     * @param o the XML input object to normalize, which could be a {@link NodeList},
     *          a single {@link Node}, or any other object
     * `@return` a JSON-compatible object. If the input is a {`@link` NodeList}, it converts
     * to a list of normalized node values (or a single unwrapped value for single-item lists).
     * If it is a single {`@link` Node}, it converts to its normalized value.
     * For other objects, they are returned as-is.
     */
    public static Object normalizeForJson(Object o) {
        switch (o) {
            case null -> {
                return null;
            }

            // XPath often returns NodeList (e.g. DTMNodeList). Convert to JSON-friendly structure.
            case NodeList nl -> {
                var arr = new ArrayList<>(nl.getLength());
                for (int i = 0; i < nl.getLength(); i++) {
                    arr.add(nodeToJsonValue(nl.item(i)));
                }
                if (arr.size() == 1) {
                    return arr.getFirst();
                }
                return arr;
            }
            case Node n -> {
                return nodeToJsonValue(n);
            }
            default -> {
            }
        }

        return o;
    }

    private static Object nodeToJsonValue(Node n) {
        if (n == null) return null;

        var value = switch (n.getNodeType()) {
            case ELEMENT_NODE -> elementToJsonValue(n);
            case ATTRIBUTE_NODE, TEXT_NODE, CDATA_SECTION_NODE -> n.getNodeValue().trim();
            default -> n.getTextContent();
        };
        var number = parseJsonNumber(value);
        if (number != null)
            return number;
        return value;
    }

    private static Object elementToJsonValue(Node element) {
        var text = element.getTextContent();
        if (text != null) {
            return text.trim();
        }
        return "";
    }

    private static Number parseJsonNumber(Object o) {
        if (!(o instanceof String s)) return null;
        try {
            var n = om.readTree(s);
            if (!n.isNumber())
                return null;
            return n.numberValue();   // returns Integer, Long, Double, BigDecimal, etc.
        } catch (Exception e) {
            return null;
        }
    }
}