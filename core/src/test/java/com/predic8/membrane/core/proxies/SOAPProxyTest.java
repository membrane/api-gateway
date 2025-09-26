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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static org.junit.jupiter.api.Assertions.*;

public class SOAPProxyTest {

    Router router;

    SOAPProxy proxy;

    @BeforeEach
    void setUp() throws IOException {
        proxy = new SOAPProxy();
        proxy.setPort(2000);
        router = new Router();
        router.setTransport(new HttpTransport());
        router.setExchangeStore(new ForgetfulExchangeStore());

        APIProxy backend = new APIProxy();
        backend.setKey(new APIProxyKey(2001));
        StaticInterceptor e = new StaticInterceptor();
        e.setTextTemplate("<foo></foo>");
        e.setContentType(TEXT_XML);
        backend.getInterceptors().add(e);
        backend.getInterceptors().add(new ReturnInterceptor());
        router.add(backend);
    }

    @AfterEach
    void shutDown() {
        router.shutdown();
    }

    @Test
    void parseWSDL() throws Exception {
        proxy.setWsdl("classpath:/ws/cities.wsdl");
        router.add(proxy);
        router.init();
    }

    @Test
    void parseWSDLWithMultiplePortsPerService() throws Exception {
        proxy.setWsdl("classpath:/blz-service.wsdl");
        router.add(proxy);
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServices() throws Exception {
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        proxy.setServiceName("CityServiceA");
        router.add(proxy);
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceA() throws Exception {
        proxy.setServiceName("CityServiceA");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.add(proxy);
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceB() throws Exception {
        proxy.setServiceName("CityServiceB");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.add(proxy);
        router.init();

        // @formatter: off
        given().when()
            .body(OpenAPITestUtils.getResourceAsStream(this, "/soap-sample/soap-request-bonn.xml"))
            .post("http://localhost:2000/city-service")
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .contentType(TEXT_XML);
        // @formatter: on
    }

    @Test
    void parseWSDLWithMultipleServicesForAWrongService() throws Exception {
        proxy.setServiceName("WrongService");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.add(proxy);
        assertThrows(IllegalArgumentException.class, () -> router.init());
    }
}