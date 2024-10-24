package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.client.b2c.B2CTestConfig;
import com.predic8.membrane.core.interceptor.oauth2.client.b2c.MockAuthorizationServer;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.oauth2client.SessionOriginalExchangeStore;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpTransport;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static com.predic8.membrane.core.interceptor.oauth2.client.b2c.MockAuthorizationServer.SERVER_PORT;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OAuth2RedirectTest {

    static final B2CTestConfig TC = new B2CTestConfig();
    static Router membraneRouter;
    static Router nginxRouter;

    @BeforeAll
    static void setup() throws Exception {
        Rule membraneRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 31337), "localhost", 2001);
        membraneRule.getInterceptors().add(new OAuth2Resource2Interceptor() {{
            setSessionManager(new InMemorySessionManager());
            setAuthService(new MembraneAuthorizationService() {{
                setSrc("http://localhost:"+ SERVER_PORT+"/"+TC.tenantId.toString()+"/"+TC.susiFlowId+"/v2.0");
                setClientSecret(TC.clientSecret);
                setClientId(TC.clientId);
                setScope("openid profile offline_access");
                setSubject("sub");
            }});
            setOriginalExchangeStore(new SessionOriginalExchangeStore());
        }});

        MockAuthorizationServer mockAuthorizationServer = new MockAuthorizationServer(
                TC,
                () -> System.out.println("Login"),
                () -> System.out.println("Logout")
        );
        mockAuthorizationServer.init();

        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2001), null, 0);
        nginxRule.getInterceptors().add(createConditionalIntercepetorWithGroovy("method == 'POST'", "exc.getResponse().setStatusCode(400)"));
        nginxRule.getInterceptors().add(createConditionalIntercepetorWithGroovy("method == 'GET'", "exc.getResponse().setStatusCode(200)"));
        nginxRule.getInterceptors().add(new ReturnInterceptor());

        membraneRouter = new Router();
        membraneRouter.setExchangeStore(new ForgetfulExchangeStore());
        membraneRouter.setTransport(new HttpTransport());
        membraneRouter.getRuleManager().addProxyAndOpenPortIfNew(membraneRule);
        membraneRouter.init();

        nginxRouter = new Router();
        nginxRouter.setExchangeStore(new ForgetfulExchangeStore());
        nginxRouter.setTransport(new HttpTransport());
        nginxRouter.getRuleManager().addProxyAndOpenPortIfNew(nginxRule);
        nginxRouter.init();
    }

    private static final String CLIENT_URL = "http://localhost:31337";
    private static final String AUTH_SERVER_URL = "http://localhost:1337";

    @Test
    void testGet() {
        // Step 1: Initial request to the client
        Response response = given()
                .redirects().follow(false)  // Don't automatically follow redirects
                .when()
                .get(CLIENT_URL)
                .then()
                .statusCode(307)  // Expect a redirect to the auth server
                .extract().response();
        
        String location = response.getHeader("Location");
        assertTrue(location != null && location.startsWith(AUTH_SERVER_URL));

        // Step 2: Simulate user authentication at the auth server
        Response authResponse = given()
                .redirects().follow(false)
                .cookies(response.getCookies())
                .when()
                .get(location)
                .then()
                .statusCode(307)  // Expect a redirect back to the client
                .extract().response();

        String clientRedirect = authResponse.getHeader("Location");
        assertTrue(clientRedirect != null && clientRedirect.startsWith(CLIENT_URL));

        // Step 3: Follow the redirect back to the client
        Response clientResponse = given()
                .cookies(response.getCookies())
                .when()
                .get(clientRedirect)
                .then()
                .statusCode(200)
                .extract().response();  // Expect successful response

        // Step 4: Make the authenticated POST request
        given()
                .cookie(clientResponse.getDetailedCookie("SESSION"))
                .when()
                .post(CLIENT_URL)
                .then()
                .statusCode(400);  // Expecting 400 as per your nginx rule
    }

    private static ConditionalInterceptor createConditionalIntercepetorWithGroovy(String test, String groovy) {
        return new ConditionalInterceptor() {{
            setLanguage(SPEL);
            setTest(test);
            setInterceptors(List.of(new GroovyInterceptor() {{
                setSrc(groovy);
            }}));
        }};
    }

    @AfterAll
    public static void tearDown() throws IOException {
        membraneRouter.shutdown();
        nginxRouter.shutdown();
    }

}
