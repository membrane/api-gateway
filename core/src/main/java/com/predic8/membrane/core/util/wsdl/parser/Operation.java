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

import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.*;

public class Operation extends WSDLElement {

    public enum Direction {
        INPUT, OUTPUT, FAULT;

        public boolean matches(String s) {
            return name().equalsIgnoreCase(s);
        }
    }

    public Operation(WSDLParserContext ctx, Node node) {
        super(ctx,node);
    }

    public List<Message> getInputs() {
        return getMessagesByDirection(INPUT);
    }

    public List<Message> getOutputs() {
        return getMessagesByDirection(OUTPUT);
    }

    public List<Message> getFaults() {
        return getMessagesByDirection(FAULT);
    }

    public List<Message> getMessagesByDirection(Direction direction) {
        var result = new ArrayList<Message>();
        var children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (isWSDLElement(child) && direction.matches(child.getLocalName())) {
                var io = (Element) child;
                var messageAttr = io.getAttribute("message");
                if (messageAttr.isEmpty()) {
                    continue;
                }
                ctx.getDefinitions().findMessage(WSDLParserUtil.resolveQName(messageAttr, io).getLocalPart())
                        .ifPresent(result::add);
            }
        }
        return result;
    }
}
