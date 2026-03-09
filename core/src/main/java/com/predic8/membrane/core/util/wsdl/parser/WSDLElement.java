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

public class WSDLElement {

    protected final WSDLParserContext ctx;
    protected final Element element;
    protected String name;

    public WSDLElement(WSDLParserContext ctx,Node node) {
        this.ctx = ctx;
        if (!(node instanceof Element element)) {
            throw new RuntimeException("Not an element: " + node.getClass());
        }
        this.element = element;
        var nameNode = element.getAttributes().getNamedItem("name");
        if (nameNode != null) {
            name = nameNode.getNodeValue();
        }
    }

    public String getName() {
        return name;
    }

    public Element getDefinitions() {
        return element.getOwnerDocument().getDocumentElement();
    }

}
