/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.util.wsdl.parser.Binding.*;
import com.predic8.membrane.core.util.wsdl.parser.Definitions.*;
import org.slf4j.*;
import org.w3c.dom.*;

import static com.predic8.membrane.core.util.wsdl.parser.Definitions.SOAPVersion.*;

public class BindingStyle extends WSDLElement {

    private static final Logger log = LoggerFactory.getLogger(BindingStyle.class);

    public BindingStyle(WSDLParserContext ctx, Node node) {
        super(ctx, node);
    }

    public Style getStyle() {
        return Style.fromString(getAttribute("style"));
    }

    public SOAPVersion getSoapVersion() {
        if (isWsdlSoap11Element()) {
            return SOAP_11;
        }
        if (isWsdlSoap12Element()) {
            return SOAP_12;
        }
        log.info("Unknown protocol of binding element in namespace: {}", this.element.getNamespaceURI());
        return UNKNOWN;
    }
}
