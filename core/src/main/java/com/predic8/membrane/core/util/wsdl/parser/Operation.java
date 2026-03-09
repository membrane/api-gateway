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

import javax.xml.namespace.*;
import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.*;
import static org.w3c.dom.Node.*;

public class Operation extends WSDLElement {

    private final List<Message> inputs;
    private final List<Message> outputs;
    private final List<Message> faults;

    public enum Direction {
        INPUT, OUTPUT;

        public boolean matches(String s) {
            return name().equalsIgnoreCase(s);
        }
    }

    public Operation(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        inputs = getInputs(node);
        outputs = getOutputs(node);
        faults = getFaults(node);
    }

    public List<Message> getInputs() {
        return inputs;
    }

    public List<Message> getOutputs() {
        return outputs;
    }

    public List<Message> getMessagesByDirection(Direction direction) {
        if (direction == INPUT)
            return inputs;
        return outputs;
    }

    public List<Message> getFaults() {
        return faults;
    }

    private List<Message> getInputs(Node node) {
        return getMessagesByDirection(node, INPUT);
    }

    private List<Message> getOutputs(Node node) {
        return getMessagesByDirection(node, OUTPUT);
    }

    private List<Message> getFaults(Node node) {
        return new ArrayList<>(); // @TODO
    }

    private List<Message> getMessagesByDirection(Node node, Direction direction) {
        var result = new ArrayList<Message>();
        var children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && direction.matches(child.getLocalName())
                && WSDL11_NS.equals(child.getNamespaceURI())) {

                Element io = (Element) child;
                String messageAttr = io.getAttribute("message");
                if (messageAttr.isEmpty()) {
                    continue;
                }

                QName messageQName = WSDLParserUtil.resolveQName(messageAttr, io);
                Message message = findMessage(messageQName, io.getOwnerDocument());
                if (message != null) {
                    result.add(message);
                }
            }
        }

        return result;
    }

    private Message findMessage(QName messageQName, Document document) {
        var definitions = document.getDocumentElement();
        var messages = definitions.getElementsByTagNameNS(WSDL11_NS, "message");

        for (int i = 0; i < messages.getLength(); i++) {
            Element message = (Element) messages.item(i);
            if (messageQName.getLocalPart().equals(message.getAttribute("name"))) {
                return new Message(ctx, message);
            }
        }

        return null;
    }
}
