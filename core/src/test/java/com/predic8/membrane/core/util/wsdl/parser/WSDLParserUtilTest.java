package com.predic8.membrane.core.util.wsdl.parser;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class WSDLParserUtilTest {

    @Test
    void typeName() {
        assertEquals("myPortType", WSDLParserUtil.getLocalName("myPortType"));
        assertEquals("myPortType", WSDLParserUtil.getLocalName("tns:myPortType"));
    }

}