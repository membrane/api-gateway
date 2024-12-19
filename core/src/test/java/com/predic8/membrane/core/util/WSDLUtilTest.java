/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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