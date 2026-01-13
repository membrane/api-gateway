package com.predic8.membrane.core.util.soap;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class WSDLUtilTest {

    @Test
    void testRewriteRelativeWsdlPath() {
        assertEquals("./a-service?wsdl", WSDLUtil.rewriteRelativeWsdlPath("./city-service?wsdl", "a-service"));
    }

}