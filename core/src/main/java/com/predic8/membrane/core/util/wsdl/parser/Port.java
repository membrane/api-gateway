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
import static org.w3c.dom.Node.*;

public class Port extends WSDLElement {

    private final Address address;
    private final Binding binding;

    public Port(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        address = getAddress(node);
        binding = getBinding(node);

    }

    public Address getAddress() {
        return address;
    }

    public Binding getBinding() {
        return binding;
    }

    public Address getAddress(Node node) {
        var children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == ELEMENT_NODE) {
                var localName = child.getLocalName();
                if ("address".equals(localName)) {
                    return new Address(ctx,child);
                }
            }
        }
        return null;
    }

    public Binding getBinding(Node node) {
        if (!(node instanceof Element port))
            return null;

        String bindingAttr = port.getAttribute("binding");
        if (bindingAttr.isEmpty())
            return null;

        var bindingQName = WSDLParserUtil.resolveQName(bindingAttr, port);
        var bindings = getDefinitions().getElementsByTagNameNS(WSDL11_NS, "binding");

        for (int i = 0; i < bindings.getLength(); i++) {
            Element binding = (Element) bindings.item(i);

            if (bindingQName.getLocalPart().equals(binding.getAttribute("name"))) {
                return new Binding(ctx, binding);
            }
        }

        return null;
    }
}
