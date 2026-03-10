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

    private final List<BindingOperation> operations;
    private final BindingStyle bindingStyle;

    public Binding(WSDLParserContext ctx, Node node) {
        super(ctx, node);
        ctx.getDefinitions().getBindings().add(this);
        operations = getBindingOperations(node);
        ctx.getDefinitions().addSoapVersion(getSoapVersion());
        bindingStyle = getBindingStyle();
    }

    public Style getStyle() {
        return bindingStyle.getStyle();
    }

    public List<BindingOperation> getOperations() {
        return operations;
    }

    public SOAPVersion getSoapVersion() {
        return getBindingStyle().getSoapVersion();
    }

    private List<BindingOperation> getBindingOperations(Node node) {
        return instantiateWSDLChildElements(node, "operation", BindingOperation.class);
    }

    private BindingStyle getBindingStyle() {
        return instantiateChildElements(element, "binding", BindingStyle.class).getFirst();
    }

    public PortType getPortType() {
        return ctx.getDefinitions().getPortTypes().stream()
                .filter(pt -> getLocalName(getAttribute("type")).equals(pt.getName()))
                .findFirst().orElseThrow(() -> new WSDLParserException("No portType found for binding: " + name));
    }
}
