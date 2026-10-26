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

import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.function.Predicate.not;

public class SoapBody extends WSDLElement {

    public SoapBody(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    /**
     * The names of the message parts listed in the parts attribute; empty if the attribute is
     * missing, in which case the body carries all parts (WSDL 1.1 &sect;3.5). An empty list means
     * the body carries no part.
     */
    public Optional<List<String>> getParts() {
        if (!element.hasAttribute("parts"))
            return Optional.empty();
        return Optional.of(Arrays.stream(getAttribute("parts").trim().split("\\s+"))
                .filter(not(String::isEmpty))
                .toList());
    }
}
