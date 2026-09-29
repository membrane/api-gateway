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

import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.openapi.util.OpenAPITestUtils;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static io.restassured.RestAssured.given;
import static io.restassured.filter.log.LogDetail.ALL;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SOAPProxyTest {

    Router router;

    SOAPProxy proxy;

    @BeforeEach
    void setUp() throws IOException {
        proxy = new SOAPProxy();
        proxy.setPort(2000);
        router = new DefaultRouter();

        APIProxy backend = new APIProxy();
        backend.setKey(new APIProxyKey(2001));
        StaticInterceptor e = new StaticInterceptor();
        e.setSrc("<foo></foo>");
        e.setContentType(TEXT_XML);
        backend.getFlow().add(e);
        backend.getFlow().add(new ReturnInterceptor());
        router.add(backend);
    }

    @AfterEach
    void shutDown() {
        router.stop();
    }

    @Test
    void parseWSDL() throws Exception {
        proxy.setWsdl("classpath:/ws/cities.wsdl");
        router.add(proxy);
        router.start();
    }

    @Test
    void parseWSDLWithMultiplePortsPerService() throws Exception {
        proxy.setWsdl("classpath:/blz-service.wsdl");
        router.add(proxy);
        router.start();
    }

    @Test
    void parseWSDLWithMultipleServices() throws Exception {
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        proxy.setServiceName("CityServiceA");
        router.add(proxy);
        router.start();
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceA() throws Exception {
        proxy.setServiceName("CityServiceA");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.add(proxy);
        router.start();
    }

    @Test
    void abstractWSDL() throws Exception {
        proxy.setWsdl("classpath:/ws/abstract-service-no-binding.wsdl");
        router.add(proxy);
        assertThrows(ConfigurationException.class,
                () -> router.start());
    }

    @Test
    void aPortWithoutAddressIsAConfigurationError() throws Exception {
        proxy.setWsdl("classpath:/ws/port-without-address.wsdl");
        router.add(proxy);

        var e = assertThrows(ConfigurationException.class, () -> router.start());
        assertTrue(e.getMessage().contains("@location"), e.getMessage());
    }

    @Test
    void aPortWithAnEmptyLocationIsAConfigurationError() throws Exception {
        proxy.setWsdl("classpath:/ws/port-empty-location.wsdl");
        router.add(proxy);

        var e = assertThrows(ConfigurationException.class, () -> router.start());
        assertTrue(e.getMessage().contains("@location"), e.getMessage());
    }

    @Test
    void theExplorerListsAPortWithoutAddress() throws Exception {
        proxy.setWsdl("classpath:/ws/navigation.wsdl");
        router.add(proxy);
        router.start();

        // @formatter: off
        given().when()
                .get("http://localhost:2000/order")
                .then()
                .log().ifValidationFails(ALL)
                .statusCode(200)
                .body(containsString("OrderPortWithoutAddress"));
        // @formatter: on
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceB() throws Exception {
        proxy.setServiceName("CityServiceB");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.add(proxy);
        router.start();

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
    void parseWSDLWithMultipleServicesForAWrongService() {
        proxy.setServiceName("WrongService");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");

        assertThrows(ConfigurationException.class, () -> {
            router.add(proxy);
            router.start();
        });
    }
}