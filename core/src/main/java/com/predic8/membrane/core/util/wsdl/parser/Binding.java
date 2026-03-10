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
import org.jetbrains.annotations.*;
import org.w3c.dom.*;

import java.util.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.wsdl.parser.Binding.Style.*;
import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.*;
import static com.predic8.membrane.core.util.wsdl.parser.WSDLParserUtil.*;
import static java.util.Optional.empty;
import static org.w3c.dom.Node.*;

public class Binding extends WSDLElement {

    private SOAPVersion soapVersion;

    public enum Style {
        RPC, DOCUMENT;

        public static Style fromString(String style) {
            return switch (style) {
                case "rpc" -> RPC;
                default -> DOCUMENT;
            };
        }
    }

    private Style style;
    private final List<BindingOperation> operations;
    private final PortType portType;

    public Binding(WSDLParserContext ctx, Node node) {
        super(ctx,node);
        ctx.getDefinitions().getBindings().add(this);
        operations = getBindingOperations(node);
        portType = getPortType(node).orElseThrow(() -> new WSDLParserException("No portType found for binding: " + name));
    }

    public Style getStyle() {
        return style;
    }

    public List<BindingOperation> getOperations() {
        return operations;
    }

    public PortType getPortType() {
        return portType;
    }

    public SOAPVersion getSoapVersion() {
        return soapVersion;
    }

    private List<BindingOperation> getBindingOperations(Node node) {
        var result = new ArrayList<BindingOperation>();
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            if (isWSDLElementWithName(child, "operation")) {
                result.add(new BindingOperation(ctx, child));
            }

            if (child.getNodeType() == ELEMENT_NODE
                && "binding".equals(child.getLocalName())) {
                style = getStyle(child);
                if (WSDL_SOAP11_NS.equals(child.getNamespaceURI())) {
                    soapVersion = SOAP_11;
                    ctx.getDefinitions().addSoapVersion(soapVersion);
                } else if (WSDL_SOAP12_NS.equals(child.getNamespaceURI())) {
                    soapVersion = SOAP_12;
                    ctx.getDefinitions().addSoapVersion(soapVersion);
                }
            }
        }
        return result;
    }

    private static Style getStyle(Node child) {
        var node = child.getAttributes().getNamedItem("style");
        if (node == null) {
            // Style is missing, assume document style.
            return DOCUMENT;
        }
        return Style.fromString(node.getNodeValue());
    }

    private Optional<PortType> getPortType(Node node) {
        if (!(node instanceof Element bindingElement)) {
            return empty();
        }
        var portTypeQName = resolveQName(getType(bindingElement), bindingElement);
        return ctx.getDefinitions().getPortTypes().stream()
                .filter(pt -> portTypeQName.getLocalPart().equals(pt.getName()))
                .findFirst();
    }

    private @NotNull String getType(Element bindingElement) {
        var type = bindingElement.getAttribute("type");
        if (type.isEmpty()) {
            throw new WSDLParserException("No type attribute found for binding: " + name);
        }
        return type;
    }
}
