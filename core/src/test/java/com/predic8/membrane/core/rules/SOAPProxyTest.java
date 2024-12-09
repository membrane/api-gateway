package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.transport.http.*;
import io.restassured.response.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

public class SOAPProxyTest {

    Router router;

    SOAPProxy proxy;

    @BeforeEach
    void setUp() {

        proxy = new SOAPProxy();
        proxy.setPort(2000);
        router = new Router();
        router.setTransport(new HttpTransport());
        router.setExchangeStore(new ForgetfulExchangeStore());
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
    void parseWSDLWithMultipleServices() throws Exception {
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceA() throws Exception {
        proxy.setServiceName("CityServiceA");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.init();
    }

    @Disabled
    @Test
    void parseWSDLWithMultipleServicesForAGivenServiceB() throws Exception {
        proxy.setServiceName("CityServiceB");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.add(proxy);
        router.init();

        System.out.println("proxy = " + proxy);

        Response res =  given().when().body(TestUtils.getResourceAsStream(this,"/soap-sample/soap-request-bonn.xml"))
                .post("http://localhost:2000/city-service");

        System.out.println("body.prettyPrint() = " + res.prettyPrint());
                res.then().statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"))
                .extract().response().body();

    }

    @Disabled
    @Test
    void parseWSDLWithMultipleServicesForAWrongService() {
        proxy.setServiceName("WrongService");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        assertThrows(IllegalArgumentException.class, () ->router.init());
    }



}