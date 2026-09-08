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

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.util.Set;

import static com.predic8.membrane.core.interceptor.schemavalidation.WSDLMessageElementExtractor.*;
import static org.junit.jupiter.api.Assertions.*;

class WSDLMessageElementExtractorTest {

    private static final String CITIES_NS = "https://predic8.de/cities";
    private static final QName GET_CITY_QNAME = new QName(CITIES_NS, "getCity");
    private static final QName GET_CITYRESPONSE_QNAME = new QName(CITIES_NS, "getCityResponse");

    private static final String CALC_NS = "http://example.com/calc";
    private static final String SVC_NS = "http://example.com/svc";

    private static final String XMAIL_NS = "urn:ws-xwebservices-com:XWebEmailValidation:EmailValidation:v2:Messages";
    private static final QName GET_EMAIL_QNAME = new QName(XMAIL_NS, "ValidateEmailRequest");
    private static final QName GET_EMAILRESPONSE_QNAME = new QName(XMAIL_NS, "ValidateEmailResponse");

    @Test
    void extract() throws Exception {
        var requestElements = getPossibleRequestElements(getDefinitions("classpath:/ws/cities.wsdl"), null);

        assertEquals(1, requestElements.size());
        assertTrue(requestElements.contains(GET_CITY_QNAME));

        var responseElements = getPossibleResponseElements(getDefinitions("classpath:/ws/cities.wsdl"), null);
        assertEquals(1, responseElements.size());
        assertTrue(responseElements.contains(GET_CITYRESPONSE_QNAME));

    }

    @Test
    void eMailServiceWSDL() throws Exception {
        var requestElements = getPossibleRequestElements(getDefinitions("classpath:/validation/XWebEmailValidation.wsdl.xml"), null);
        assertEquals(1, requestElements.size());
        assertTrue(requestElements.contains(GET_EMAIL_QNAME));

        var responseElements = getPossibleResponseElements(getDefinitions("classpath:/validation/XWebEmailValidation.wsdl.xml"), null);
        assertEquals(1, responseElements.size());
        assertTrue(responseElements.contains(GET_EMAILRESPONSE_QNAME));
    }

    @Test
    void rpcStyle() throws Exception {
        var requestElements = getPossibleRequestElements(getDefinitions("classpath:/validation/inline-anytype.wsdl"), "Hello_Service");
        assertEquals(1, requestElements.size());
        assertTrue(requestElements.contains(new QName("http://www.examples.com/wsdl/HelloService.wsdl", "sayHello")));
    }

    @Test
    void faultDetailElements() throws Exception {
        var faultElements = getPossibleFaultDetailElements(getDefinitions("classpath:/ws/cities-with-fault.wsdl"), null);

        assertEquals(1, faultElements.size());
        assertTrue(faultElements.contains(new QName(CITIES_NS, "cityNotFound")));
    }

    /**
     * A {@code soap:fault} is never RPC-wrapped, so even under an RPC binding the fault detail
     * element is the fault message's part element - not the operation name, which is what the RPC
     * wrapper rule yields for input/output.
     */
    @Test
    void faultDetailElementsRpcStyle() throws Exception {
        var definitions = getDefinitions("classpath:/ws/rpc-with-fault.wsdl");

        assertEquals(Set.of(new QName(CALC_NS, "divByZero")), getPossibleFaultDetailElements(definitions, null));
        assertEquals(Set.of(new QName(CALC_NS, "divide")), getPossibleRequestElements(definitions, null));
    }

    /**
     * All faults of an operation contribute their detail element, not just the first.
     */
    @Test
    void twoFaultsOnOneOperation() throws Exception {
        assertEquals(Set.of(new QName(CITIES_NS, "cityNotFound"), new QName(CITIES_NS, "rateLimited")),
                getPossibleFaultDetailElements(getDefinitions("classpath:/ws/two-faults-per-operation.wsdl"), null));
    }

    @Test
    void noFaultDeclared() throws Exception {
        assertTrue(getPossibleFaultDetailElements(getDefinitions("classpath:/ws/cities.wsdl"), null).isEmpty());
    }

    /**
     * A fault whose part names a type has no element to validate its detail against. Dropping it
     * would make the result indistinguishable from a WSDL declaring no faults, silently disabling
     * fault validation - so it is rejected instead.
     */
    @Test
    void faultPartNamingATypeIsRejected() throws Exception {
        var definitions = getDefinitions("classpath:/ws/fault-part-with-type.wsdl");

        var e = assertThrows(ConfigurationException.class, () -> getPossibleFaultDetailElements(definitions, null));
        assertTrue(e.getMessage().contains("NotFoundFault"), e.getMessage());
        assertTrue(e.getMessage().contains("names a type instead of an element"), e.getMessage());
    }

    /**
     * Only the first part of a fault message is ever validated, so the extra ones must be reported
     * at startup instead of looking covered.
     */
    @Test
    void faultMessageWithSeveralPartsWarnsAndUsesTheFirst() throws Exception {
        var root = (Logger) LogManager.getRootLogger();
        var appender = new TestAppender("WSDLMessageElementExtractorTest");
        appender.start();
        root.addAppender(appender);
        try {
            var elements = getPossibleFaultDetailElements(getDefinitions("classpath:/ws/fault-message-with-two-parts.wsdl"), null);

            assertEquals(Set.of(new QName(SVC_NS, "notFound")), elements);
            assertTrue(appender.contains("declares 2 parts"), appender.getMessages().toString());
            assertTrue(appender.contains("NotFoundFault"), appender.getMessages().toString());
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void faultMessageWithoutPartIsRejected() throws Exception {
        var definitions = getDefinitions("classpath:/ws/fault-message-without-part.wsdl");

        var e = assertThrows(ConfigurationException.class, () -> getPossibleFaultDetailElements(definitions, null));
        assertTrue(e.getMessage().contains("NotFoundFault"), e.getMessage());
        assertTrue(e.getMessage().contains("has no part"), e.getMessage());
    }

    private static @NotNull Definitions getDefinitions(String location) throws Exception {
        return Definitions.parse(new ResolverMap(), location);
    }

}