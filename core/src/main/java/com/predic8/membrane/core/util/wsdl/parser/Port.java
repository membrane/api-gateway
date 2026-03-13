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

import static com.predic8.membrane.core.util.wsdl.parser.WSDLParserUtil.getLocalName;

public class Port extends WSDLElement {

    public Port(WSDLParserContext ctx, Node node) {
        super(ctx,node);
    }

    public Address getAddress() {
        return instantiateElements(element,"address",Address.class).getFirst();
    }

    public Binding getBinding() {
        return ctx.definitions().getBindings().stream()
                .filter(this::matchesTypeAttribute)
                .findFirst().orElseThrow(() -> new WSDLParserException("No binding found for port: " + getName()));
    }

    private boolean matchesTypeAttribute(Binding b) {
        return b.getName().equals(getLocalName(getAttribute("binding")));
    }
}
