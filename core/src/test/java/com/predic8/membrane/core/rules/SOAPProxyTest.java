package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.openapi.util.TestUtils;
import com.predic8.membrane.core.transport.http.*;
import io.restassured.response.ResponseBody;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SOAPProxyTest {

    Router router;

    SOAPProxy proxy;

    @BeforeEach
    void setUp() throws Exception {

        proxy = new SOAPProxy();
        proxy.setPort(2000);
        router = new Router();
        router.setTransport(new HttpTransport());
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.add(proxy);
    }

    @AfterEach
    void shutDown() {
        router.stop();
    }

    @Test
    void parseWSDL() throws Exception {
        proxy.setWsdl("classpath:/ws/cities.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultiplePortsPerService() throws Exception {
        proxy.setWsdl("classpath:/blz-service.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServices() {
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        assertThrows(IllegalArgumentException.class, () -> router.init());
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceA() throws Exception {
        proxy.setServiceName("CityServiceA");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceB() throws Exception {
        proxy.setServiceName("CityServiceB");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServicesForAWrongService() {
        proxy.setServiceName("WrongService");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        assertThrows(IllegalArgumentException.class, () ->router.init());
    }

}