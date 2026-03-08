package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.junit.jupiter.api.*;

import javax.xml.namespace.*;
import java.io.*;

import static com.predic8.membrane.core.interceptor.schemavalidation.WSDLMessageElementExtractor.*;
import static org.junit.jupiter.api.Assertions.*;

class WSDLMessageElementExtractorTest {

    private static final String CITIES_NS = "https://predic8.de/cities";
    private static final QName GET_CITY_QNAME = new QName(CITIES_NS, "getCity");
    private static final QName GET_CITYRESPONSE_QNAME = new QName(CITIES_NS, "getCityResponse");

    private static final String XMAIL_NS = "urn:ws-xwebservices-com:XWebEmailValidation:EmailValidation:v2:Messages";
    private static final QName GET_EMAIL_QNAME = new QName(XMAIL_NS, "ValidateEmailRequest");
    private static final QName GET_EMAILRESPONSE_QNAME = new QName(XMAIL_NS, "ValidateEmailResponse");

    @Test
    void extract() throws Exception {
        var location = "/ws/cities.wsdl";
        try (var wsdl = getClass().getResourceAsStream(location)) {
            var doc = parseWsdl(wsdl, location);
            var requestElements = getPossibleRequestElements(doc, null);

            assertEquals(1, requestElements.size());
            assertTrue(requestElements.contains(GET_CITY_QNAME));

            var responseElements = getPossibleResponseElements(doc, null);
            assertEquals(1, responseElements.size());
            assertTrue(responseElements.contains(GET_CITYRESPONSE_QNAME));
        }
    }

    @Test
    void eMailServiceWSDL() throws Exception {
        var doc = Definitions.parse(new ResolverMap(), "classpath:/validation/XWebEmailValidation.wsdl.xml");
        var requestElements = getPossibleRequestElements(doc, null);
        assertEquals(1, requestElements.size());
        assertTrue(requestElements.contains(GET_EMAIL_QNAME));

        var responseElements = getPossibleResponseElements(doc, null);
        assertEquals(1, responseElements.size());
        assertTrue(responseElements.contains(GET_EMAILRESPONSE_QNAME));

    }

    @Test
    void rpcStyle() throws Exception {
        var location = "/validation/inline-anytype.wsdl";
        try (var wsdl = getClass().getResourceAsStream(location)) {
            var doc = parseWsdl(wsdl, location);
            var requestElements = getPossibleRequestElements(doc, "Hello_Service");
            assertEquals(1, requestElements.size());
            assertTrue(requestElements.contains(new QName("http://www.examples.com/wsdl/HelloService.wsdl", "sayHello")));
        }
    }

    private static Definitions parseWsdl(InputStream is, String location) {
        var ctx = new WSDLParserContext(null, new ResolverMap(), location);
        var definitions = new Definitions(ctx);
        definitions.parse(is, null);
        return definitions;
    }
}