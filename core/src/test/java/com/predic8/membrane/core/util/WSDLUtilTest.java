package com.predic8.membrane.core.util;

import com.predic8.wsdl.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import javax.xml.namespace.*;
import java.util.*;

import static com.predic8.membrane.core.util.WSDLUtil.*;
import static com.predic8.membrane.core.util.WSDLUtil.Direction.*;
import static org.junit.jupiter.api.Assertions.*;

public class WSDLUtilTest {

    public static final String PREDIC_NS = "https://predic8.de/";

    static Definitions definitions;

    @BeforeAll
    static void beforeAll() {
        WSDLParserContext ctx = new WSDLParserContext();
        ctx.setInput("src/test/resources/ws/two-separated-services.wsdl");
        WSDLParser wsdlParser = new WSDLParser();
        definitions = wsdlParser.parse(ctx);
    }

    @Test
    void getServiceA() {
        testGetServiceByName("ServiceA");
    }

    @Test
    void getServiceB() {
        testGetServiceByName("ServiceB");
    }

    @Test
    void getNonExistingService() {
        assertThrows(RuntimeException.class, () -> getService(definitions, "not there"));
    }

    private static void testGetServiceByName(String ServiceA) {
        Service service = getService(definitions, ServiceA);
        assertNotNull(service);
        assertEquals(ServiceA, service.getName());
    }

    @Test
    void getPossibleSOAPElementsARequest() {
        Set<QName> el = getSoapElements("ServiceA",REQUEST);
        assertNotNull(el);
        assertEquals(1,el.size());
        assertTrue(el.contains(new QName(PREDIC_NS,"a")));
    }

    @Test
    void getPossibleSOAPElementsAResponse() {
        Set<QName> el = getSoapElements("ServiceA", RESPONSE);
        assertNotNull(el);
        assertEquals(1,el.size());
        assertTrue(el.contains(new QName(PREDIC_NS,"aResponse")));
    }

    @Test
    void getPossibleSOAPElementsBRequest() {
        Set<QName> el = getSoapElements("ServiceB", REQUEST);
        assertNotNull(el);
        assertEquals(1,el.size());
        assertTrue( el.contains(new QName(PREDIC_NS,"b")));
    }

    private static @NotNull Set<QName> getSoapElements(String serviceName, Direction direction) {
        return getPossibleSOAPElements(getService(definitions, serviceName), direction);
    }
}