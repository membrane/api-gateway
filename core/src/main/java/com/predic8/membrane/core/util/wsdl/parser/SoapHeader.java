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

import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.util.wsdl.parser.WSDLParserUtil.getLocalName;

public class SoapHeader extends WSDLElement {

    public SoapHeader(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    public String getPart() {
        return getAttribute("part");
    }

    public String getMessage() {
        return getAttribute("message");
    }

    /**
     * The soap:headerfault children: the header blocks of a fault about this header block. They
     * have the same attributes as a soap:header.
     */
    public List<SoapHeader> getHeaderFaults() {
        return instantiateChildren("headerfault", SoapHeader.class);
    }

    /** The message part the header carries; empty if the message or the part cannot be found. */
    public Optional<Part> resolvePart() {
        return ctx.definitions().findMessage(getLocalName(getMessage()))
                .flatMap(message -> message.getParts().stream()
                        .filter(p -> getPart().equals(p.getName()))
                        .findFirst());
    }
}
