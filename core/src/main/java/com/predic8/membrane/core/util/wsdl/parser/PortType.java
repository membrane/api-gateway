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

import com.predic8.membrane.annot.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.WSDL11_NS;
import static org.w3c.dom.Node.*;

public class PortType extends WSDLElement {

    private final List<Operation> operations;

    public PortType(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        operations = getOperations(node);
        ctx.getDefinitions().getPortTypes().add(this);
    }

    public List<Operation> getOperations() {
        return operations;
    }

    private List<Operation> getOperations(Node node) {
        var operations = new ArrayList<Operation>();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE
                && "operation".equals(child.getLocalName())
                && WSDL11_NS.equals(child.getNamespaceURI())
            ) {
                operations.add(new Operation(ctx, child));
            }
        }

        return operations;
    }
}
