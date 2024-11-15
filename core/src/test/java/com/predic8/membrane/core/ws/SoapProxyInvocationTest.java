package com.predic8.membrane.core.ws;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.rules.*;
import io.restassured.response.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class SoapProxyInvocationTest {

    static Router gw;
    static Router n1;

    static Exchange last;

    static SOAPProxy soapProxy;

    @BeforeAll
    public static void setup() throws Exception {

        n1 = new HttpRouter();
        APIProxy api = new APIProxy();
        api.setPort(2001);
        api.getInterceptors().add(new SampleSoapServiceInterceptor());
        n1.getRuleManager().addProxyAndOpenPortIfNew(api);
        n1.init();

        gw = new HttpRouter();
        gw.setHotDeploy(false);
        soapProxy = new SOAPProxy();
        soapProxy.setPort(2000);
        soapProxy.setWsdl("classpath:/ws/cities.wsdl");
        soapProxy.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                System.out.println("exc.getDestinations() = " + exc.getDestinations());
                last = exc;
                return CONTINUE;
            }
        });

        ValidatorInterceptor e = new ValidatorInterceptor();
        e.setWsdl("classpath:/ws/cities-2-services.wsdl");

        RequestInterceptor ri = new RequestInterceptor();
        ri.getInterceptors().add(e);

        soapProxy.getInterceptors().add(ri);

        gw.getRuleManager().addProxyAndOpenPortIfNew(soapProxy);
    }

    @AfterAll
    public static void teardown() throws IOException {
        gw.shutdown();
        n1.shutdown();
    }

    @Disabled
    @Test
    void WSDLRewriting() throws Exception {
        // @formatter:off
        given()
            .get("http://localhost:2000/?wsdl")
        .then()
            .statusCode(200)
            .contentType(TEXT_XML)
            .body("definitions.service.port.address.@location", equalTo("http://localhost:2000/"));
        // @formatter:on
    }

    @Disabled
    @Test
    void callService() {
        // @formatter:off
        ResponseBody body =  given().when().body(TestUtils.getResourceAsStream(this,"/soap-sample/soap-request-bonn.xml"))
                .post("http://localhost:2000/services/cities").then()
                .statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"))
                .extract().response().body();
        
        System.out.println("body.prettyPrint() = " + body.prettyPrint());
        
        System.out.println("last.getDestinations() = " + last.getDestinations()); // @TODO assert
              
    }

    @Disabled
    @Test
    void twoServicesA() throws Exception {
        callService("CityServiceA", "/services/cities","Bonn");
    }

    @Disabled
    @Test
    void twoServicesB() throws Exception {
        callService("CityServiceB", "/city-service","New York City");
    }

    private static void callService(String serviceName, String path, String city)throws Exception {
        soapProxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        soapProxy.setServiceName(serviceName);
        gw.init();

        System.out.println(city);

        Response res =  given().when()
                .body("""
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                        <s:Body>
                            <cs:getCity>
                                <name>%s</name>
                            </cs:getCity>
                        </s:Body>
                    </s:Envelope>
                    """.formatted(city))
                .post("http://localhost:2000" + path);

        System.out.println(res.prettyPrint());

        res.then().statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"))
                .extract().response().body();}
}
