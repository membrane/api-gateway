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
package com.predic8.membrane.core.ws;

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.lang.SetHeaderInterceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor;
import com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.SOAPProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_PROBLEM_XML;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePorts;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class SoapProxyInvocationTest {

    public static final String SERVICE_A_REQUEST = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                    <s:Body>
                        <ns:a xmlns:ns="https://predic8.de/">Paris</ns:a>
                    </s:Body>
                </s:Envelope>""";

    public static final String SERVICE_A_INVALID_REQUEST = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                    <s:Body>
                        <ns:a xmlns:ns="https://predic8.de/"><invalid/></ns:a>
                    </s:Body>
                </s:Envelope>""";

    public static final String SERVICE_B_REQUEST = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                    <s:Body>
                        <ns:b xmlns:ns="https://predic8.de/">Rio</ns:b>
                    </s:Body>
                </s:Envelope>""";

    static Router gw;
    static Router backend;
    static int port, backendPort;

    @BeforeAll
    public static void setup() throws Exception {
        int[] ports = freePorts(2);
        port = ports[0];
        backendPort = ports[1];
        setupBackend();
        setupGateway();
    }

    private static void setupGateway() throws Exception {
        gw = new TestRouter();
        gw.getConfiguration().setHotDeploy(false);
        gw.add(createCitiesSoapProxyGateway());
        gw.add(createTwoServicesSOAPProxyGateway("ServiceA"));
        gw.start();
        // SOAP initialization reads target ports from the shared WSDL fixtures.
        gw.getRuleManager().getRules().forEach(rule ->
                ((SOAPProxy) rule).getTarget().setPort(backendPort));
    }

    private static @NotNull SOAPProxy createCitiesSoapProxyGateway() {
        SOAPProxy soapProxy = new SOAPProxy();
        soapProxy.setPort(port);
        soapProxy.setWsdl("classpath:/ws/cities.wsdl");
        return soapProxy;
    }

    private static @NotNull SOAPProxy createTwoServicesSOAPProxyGateway(String serviceName) {
        SOAPProxy sp = new SOAPProxy();
        sp.setPort(port);
        sp.setWsdl("classpath:/ws/two-separated-services.wsdl");
        sp.setServiceName(serviceName);
        sp.getFlow().add(new ValidatorInterceptor());
        return sp;
    }

    private static void setupBackend() throws Exception {
        backend = new TestRouter();
        backend.add(createAServiceProxy());
        backend.add(createCitiesServiceProxy());
        backend.start();
    }

    private static @NotNull APIProxy createCitiesServiceProxy() {
        APIProxy api = new APIProxy();
        api.setPort(backendPort);
        api.getFlow().add(new SampleSoapServiceInterceptor());
        return api;
    }

    private static @NotNull APIProxy createAServiceProxy() {
        APIProxy aServiceAPI = new APIProxy();
        Path p2 = new Path();
        p2.setUri("/services/a");
        aServiceAPI.setPath(p2 );
        aServiceAPI.setPort(backendPort);
        aServiceAPI.getFlow().add(new ResponseInterceptor() {{
            setFlow(List.of(new SetHeaderInterceptor() {{
                setFieldName("AService");
                setValue("123");
            }}));
        }});
        aServiceAPI.getFlow().add(new ResponseInterceptor() {{
            setFlow(List.of(new TemplateInterceptor() {{
                setSrc("""
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cs="https://predic8.de/cities">
                    <s:Body>
                        <ns:aResponse xmlns:ns="https://predic8.de/">Panama</ns:aResponse>
                    </s:Body>
                </s:Envelope>""");
                setContentType(TEXT_XML);
            }}));
            }});

        aServiceAPI.getFlow().add(new ReturnInterceptor());
        return aServiceAPI;
    }

    @AfterAll
    public static void teardown() {
        if (gw != null) gw.stop();
        if (backend != null) backend.stop();
    }

    @Test
    void WSDLRewriting() {
        // @formatter:off
        ValidatableResponse res = given()
            .get("http://localhost:" + port + "/services/cities?wsdl")
        .then();

            res.statusCode(200)
            .contentType(TEXT_XML)
            .body("definitions.service.port.address.@location", equalTo("http://localhost:" + port + "/services/cities"));
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
            .post("http://localhost:" + port + "/services/cities");

            body.then()
                .statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.getCityResponse.country", equalTo("Germany"))
                .body("Envelope.Body.getCityResponse.population", equalTo("327000"))
                .extract().response().body();
    }

    @Test
    void twoServicesA()  {

        Response res =  given().when()
                .body(SERVICE_A_REQUEST)
                .contentType(TEXT_XML)
                .post("http://localhost:" + port + "/services/a");

        res.then().statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.aResponse", equalTo("Panama"));
    }

    @Test
    void twoRequestToSecondNotDeployedService()  {
        // @formatter:off
        given().when()
                .body(SERVICE_B_REQUEST)
                .contentType(TEXT_XML)
                .post("http://localhost:" + port + "/services/b")  // This service is not selected!
        .then().statusCode(404)
                .log().ifValidationFails()
                .contentType(APPLICATION_PROBLEM_XML)
                .body("error.title", equalTo("Invalid path or method"));
        // @formatter:on
    }

    @Test
    void twoServicesAInvalidRequest()  {
        // @formatter:off
        given().when()
                .body(SERVICE_A_INVALID_REQUEST)
                .contentType(TEXT_XML)
                .post("http://localhost:" + port + "/services/a")
        .then().statusCode(200)
                .contentType(TEXT_XML)
                .body("Envelope.Body.Fault.faultcode", equalTo("soap:Client"))
                .body("Envelope.Body.Fault.faultstring", equalTo("WSDL message validation failed"));
        // @formatter:on
    }
}
