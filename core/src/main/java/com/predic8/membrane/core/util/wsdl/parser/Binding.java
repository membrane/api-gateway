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

import com.predic8.membrane.core.util.wsdl.parser.Definitions.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.core.util.wsdl.parser.WSDLParserUtil.*;

public class Binding extends WSDLElement {

    public enum Style {
        RPC, DOCUMENT;

        public static Style fromString(String style) {
            return switch (style) {
                case "rpc" -> RPC;
                default -> DOCUMENT;
            };
        }
    }

    public Binding(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    public Style getStyle() {
        return getBindingStyle().getStyle();
    }

    public SOAPVersion getSoapVersion() {
        return getBindingStyle().getSoapVersion();
    }

    public BindingOperation getBindingOperation(String name) {
        return getBindingOperations().stream()
                .filter(bo -> bo.getName().equals(name))
                .findFirst().orElseThrow(() -> new WSDLParserException("No bindingOperation found for name: " + name));
    }

    public List<BindingOperation> getBindingOperations() {
        return instantiateWSDLChildren("operation", BindingOperation.class);
    }

    private BindingStyle getBindingStyle() {
        var binding = instantiateChildren("binding", BindingStyle.class);
        if (binding.isEmpty())
            throw new WSDLParserException("No bindingStyle found for binding: " + getName());
        return binding.getFirst();
    }

    public PortType getPortType() {
        return ctx.definitions().getPortTypes().stream()
                .filter(pt -> getLocalName(getAttribute("type")).equals(pt.getName()))
                .findFirst().orElseThrow(() -> new WSDLParserException("No portType found for binding: " + getName()));
    }
}
