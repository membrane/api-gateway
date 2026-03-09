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

package com.predic8.membrane.core.util.wsdl.parser;

import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;

public class Message extends WSDLElement {

    private final List<Part> parts = new ArrayList<>();

    public Message(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        this.parts.add(getPart(node));
        ctx.getDefinitions().getMessages().add(this);
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
