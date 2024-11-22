package com.predic8.membrane.core.ws;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.misc.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.rules.*;
import io.restassured.response.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class SoapProxyInvocationTest {

    static Router gw;
    static Router backend;

    @BeforeAll
    public static void setup() throws Exception {
        setupBackend();
        setupGateway();
    }

    private static void setupGateway() throws Exception {
        gw = new HttpRouter();
        gw.setHotDeploy(false);
        gw.getRuleManager().addProxyAndOpenPortIfNew(createCitiesSoapProxyGateway());
        gw.getRuleManager().addProxyAndOpenPortIfNew(createTwoServicesSOAPProxyGateway("ServiceA"));
        gw.init();
    }

    private static @NotNull SOAPProxy createCitiesSoapProxyGateway() {
        SOAPProxy soapProxy = new SOAPProxy();
        soapProxy.setPort(2000);
        soapProxy.setWsdl("classpath:/ws/cities.wsdl");
        return soapProxy;
    }

    private static @NotNull SOAPProxy createTwoServicesSOAPProxyGateway(String serviceName) {
        SOAPProxy sp = new SOAPProxy();
        sp.setPort(2000);
        sp.setWsdl("classpath:/ws/two-separated-services.wsdl");
        sp.setServiceName(serviceName);
        return sp;
    }

    private static void setupBackend() throws Exception {
        backend = new HttpRouter();
        backend.getRuleManager().addProxyAndOpenPortIfNew(createAServiceProxy());
        backend.getRuleManager().addProxyAndOpenPortIfNew(createCitiesServiceProxy());
        backend.init();
    }

    private static @NotNull APIProxy createCitiesServiceProxy() {
        APIProxy api = new APIProxy();
        api.setPort(2001);
        api.getInterceptors().add(new SampleSoapServiceInterceptor());
        return api;
    }

    private static @NotNull APIProxy createAServiceProxy() {
        APIProxy aServiceAPI = new APIProxy();
        Path p2 = new Path();
        p2.setValue("/services/a");
        aServiceAPI.setPath(p2 );
        aServiceAPI.setPort(2001);
        aServiceAPI.getInterceptors().add(new ResponseInterceptor() {{
            setInterceptors(List.of(new SetHeaderInterceptor() {{
                setName("AService");
                setValue("123");
            }}));
        }});
        aServiceAPI.getInterceptors().add(new ResponseInterceptor() {{
            setInterceptors(List.of(new LogInterceptor()));
        }});

        aServiceAPI.getInterceptors().add(new TemplateInterceptor() {{
                setTextTemplate("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                    <s:Body>
                        <ns:aResponse xmlns:ns="https://predic8.de/">Correct!</ns:aResponse>
                    </s:Body>
                </s:Envelope>""");
                setContentType(TEXT_XML);
            }});

//        aServiceAPI.getInterceptors().add(new ResponseInterceptor() {{
//            setInterceptors(List.of(new TemplateInterceptor() {{
//                setTextTemplate("""
//                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
//                    <s:Body>
//                        <ns:aResponse xmlns:ns="https://predic8.de/">Correct!</ns:aResponse>
//                    </s:Body>
//                </s:Envelope>""");
//                setContentType(TEXT_XML);
//            }}));
//            }});

        aServiceAPI.getInterceptors().add(new ReturnInterceptor());
        return aServiceAPI;
    }



    @AfterAll
    public static void teardown() throws IOException {
        gw.shutdown();
        backend.shutdown();
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
        Response body =  given().when()
            .contentType(TEXT_XML)
            .body("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                    <s:Body>
                        <cs:getCity>
                            <name>Bonn</name>
                        </cs:getCity>
                    </s:Body>
                </s:Envelope>""")
            .post("http://localhost:2000/services/cities");

            body.then()
                .statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"))
                .body("Envelope.Body.getCityResponse.population", equalTo("327000"))
                .extract().response().body();
    }

    @Test
    void twoServicesA()  {

        String s = """
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                        <s:Body>
                            <ns:a xmlns:ns="https://predic8.de/">Paris!</ns:a>
                        </s:Body>
                    </s:Envelope>""";

//                String s = """
//                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><ns:a xmlns:ns="https://predic8.de/">Paris!</ns:a></s:Body></s:Envelope>""";

//        String s = "ParisErsatz!";

        Response res =  given().when()
                .body(s)
                .headers("Paris", "Anfrage")
                .contentType(TEXT_XML)
                .post("http://localhost:2000/services/a");

        System.out.println("res.prettyPrint() = " + res.prettyPrint());

        res.then().statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.aResponse", equalTo("Correct!"));
    }


}
