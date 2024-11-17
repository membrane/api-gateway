package com.predic8.membrane.core.ws;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
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
    static SOAPProxy aServiceProxy;

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
            public Outcome handleRequest(Exchange exc) {
                last = exc;
                return CONTINUE;
            }
        });

        aServiceProxy = new SOAPProxy();
        aServiceProxy.setPort(2000);
        aServiceProxy.setWsdl("classpath:/ws/two-separated-services.wsdl");
        aServiceProxy.setServiceName("ServiceA");

        ValidatorInterceptor e = new ValidatorInterceptor();
        e.setWsdl("classpath:/ws/cities-2-services.wsdl");

        RequestInterceptor ri = new RequestInterceptor();
        ri.getInterceptors().add(e);

        soapProxy.getInterceptors().add(ri);

        gw.getRuleManager().addProxyAndOpenPortIfNew(soapProxy);
        gw.getRuleManager().addProxyAndOpenPortIfNew(aServiceProxy);
        gw.init();
    }

    @AfterAll
    public static void teardown() throws IOException {
        gw.shutdown();
        n1.shutdown();
    }

    @Test
    void WSDLRewriting() {
        // @formatter:off
        ValidatableResponse res = given()
            .get("http://localhost:2000/services/cities?wsdl")
        .then();

            res.statusCode(200)
            .contentType(TEXT_XML)
            .body("definitions.service.port.address.@location", equalTo("http://localhost:2000/services/cities"));
        // @formatter:on
    }

    @Test
    void callService() {
        // @formatter:off
        Response body =  given().when().body("""
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                <s:Body>
                    <cs:getCity>
                        <name>Bonn</name>
                    </cs:getCity>
                </s:Body>
            </s:Envelope>
            """)
                .post("http://localhost:2000/services/cities");
        
        System.out.println("body.prettyPrint() = " + body.prettyPrint());
        
            body.then()
                .statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"))
                .body("Envelope.Body.getCityResponse.population", equalTo("327000"))
                .extract().response().body();
    }

    @Test
    void twoServicesA() throws Exception {

        Response res =  given().when()
                .body("""
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                        <s:Body>
                            <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a> 
                        </s:Body>
                    </s:Envelope>
                    """.formatted("Bonn"))
                .post("http://localhost:2000/services/a");

        res.then().statusCode(200)
                .contentType(TEXT_XML);
    }

    @Disabled
    @Test
    void twoServicesB() throws Exception {
        callService("CityServiceB", "/city-service","New York City");
    }

    private static void callService(String serviceName, String path, String city)throws Exception {


        Response res =  given().when()
                .body("""
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                        <s:Body>
                            <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a> 
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
