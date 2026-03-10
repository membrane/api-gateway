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
