package com.predic8.membrane.core.ws;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.rules.*;
import io.restassured.response.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class SoapProxyInvocationTest {

    static Router gw;
    static Router n1;
    
    static Exchange last;

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
        SOAPProxy soapProxy = new SOAPProxy();
        soapProxy.setPort(2000);
        soapProxy.setWsdl("classpath:/ws/cities.wsdl");
        soapProxy.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                System.out.println("exc.getDestinations() = " + exc.getDestinations());
                last = exc;
                return Outcome.CONTINUE;
            }
        });

        gw.getRuleManager().addProxyAndOpenPortIfNew(soapProxy);
        gw.init();
    }

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
}
