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

public class Service extends WSDLElement {

    public static final String PORT = "port";
    private final List<Port> ports;

    public Service(WSDLParserContext ctx, Node element) {
        super(ctx,element);
        ports = getPorts(element);
        ctx.getDefinitions().services.add(this);
    }

    public List<Port> getPorts() {
        return ports;
    }

    public List<Port> getPorts(Node service) {
        var ports = new ArrayList<Port>();
        var children = service.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var node = children.item(i);
            if (node instanceof Element e &&
                PORT.equals(e.getLocalName()) && WSDL11_NS.equals(e.getNamespaceURI())) {
                ports.add(new Port(ctx,e));
            }
        }
        return ports;
    }
}
