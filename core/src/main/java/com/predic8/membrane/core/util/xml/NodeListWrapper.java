/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import org.w3c.dom.*;

/**
 * Wraps a XML DOM NodeList to provide a toString suitable to use in a
 * template.
 */
public class NodeListWrapper implements NodeList {

    final NodeList nodeList;

    public NodeListWrapper(NodeList nodeList) {
        this.nodeList = nodeList;
    }

    @Override
    public Node item(int index) {
        return nodeList.item(index);
    }

    @Override
    public int getLength() {
        return nodeList.getLength();
    }

    @Override
    public String toString() {
        return nodeListToPlainText(nodeList);
    }

    public static String nodeListToPlainText(NodeList nodeList) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            appendTextNodeContent(node, builder);
        }
        return builder.toString().trim();
    }

    private static void appendTextNodeContent(Node node, StringBuilder builder) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            String textContent = node.getTextContent().trim();
            if (!textContent.isEmpty()) {
                builder.append(textContent).append(" ");
            }
        } else if (node.hasChildNodes()) {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                appendTextNodeContent(children.item(i), builder);
            }
        }
    }
}
