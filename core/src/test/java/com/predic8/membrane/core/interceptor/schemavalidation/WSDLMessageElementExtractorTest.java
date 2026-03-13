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

import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import javax.xml.namespace.*;

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

    private static @NotNull Definitions getDefinitions(String location) throws Exception {
        return Definitions.parse(new ResolverMap(), location);
    }

}