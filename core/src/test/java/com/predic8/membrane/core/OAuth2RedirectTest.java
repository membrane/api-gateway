package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.ClaimList;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.StaticClientList;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerTokenGenerator;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.interceptor.LogInterceptor.Level.DEBUG;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OAuth2RedirectTest {

    static Router membraneRouter;
    static Router azureRouter;
    static Router nginxRouter;

    @BeforeAll
    static void setup() throws Exception {
        Rule membraneRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), "localhost", 2001);
        membraneRule.getInterceptors().add(new OAuth2Resource2Interceptor() {{
            setSessionManager(new InMemorySessionManager());
            setAuthService(new MembraneAuthorizationService() {{
                setSrc("http://localhost:2002");
                setClientId("abc");
                setClientSecret("def");
                setScope("openid profile");
                setSubject("sub");
            }});
            setOriginalExchangeStore(new SessionOriginalExchangeStore());
        }});

        Rule azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2002),  "localhost", 80);
        azureRule.getInterceptors().add(new LogInterceptor() {{
            setLevel(DEBUG);
        }});
        azureRule.getInterceptors().add(
            new OAuth2AuthorizationServerInterceptor() {{
                setLoginViewDisabled(true);
                setTokenGenerator(new BearerTokenGenerator());
                setIssuer("http://localhost:2002");
                setUserDataProvider(
                    new StaticUserDataProvider() {{
                        setUsers(List.of(new User() {{
                            setUsername("user");
                            setPassword("password");
                        }}));
                    }}
                );
                setClientList(
                    new StaticClientList() {{
                        setClients(List.of(new Client() {{
                            setClientId("abc");
                            setClientSecret("def");
                            setCallbackUrl("http://localhost:2000/oauth2callback");
                        }}));
                    }}
                );
                setClaimList(new ClaimList() {{
                    setValue("aud email iss sub username");
                    setScopes(new ArrayList<>() {{
                        add(new Scope() {{
                            setId("username");
                            setClaims("username");
                        }});
                        add(new Scope() {{
                            setId("profile");
                            setClaims("username email");
                        }});
                    }});
                }});
            }}
        );

        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2001), "localhost", 80);
        nginxRule.getInterceptors().add(createConditionalInterceptorWithGroovy("method == 'POST'", "exc.getResponse().setStatusCode(400)"));
        nginxRule.getInterceptors().add(createConditionalInterceptorWithGroovy("method == 'GET'", "exc.getResponse().setStatusCode(200)"));
        nginxRule.getInterceptors().add(new ReturnInterceptor());

        azureRouter = new Router();
        azureRouter.setExchangeStore(new ForgetfulExchangeStore());
        azureRouter.setTransport(new HttpTransport());
        azureRouter.getRuleManager().addProxyAndOpenPortIfNew(azureRule);
        azureRouter.init();

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

    private static final String CLIENT_URL = "http://localhost:2000";
    private static final String AUTH_SERVER_URL = "http://localhost:2002";

    @Test
    void wart() throws InterruptedException {
        Thread threde = new Thread(() -> {
            try {
                Thread.sleep(4573894);
            } catch (Exception e) {}
        });
        threde.start();
        threde.join();
    }

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
        System.out.println("location = " + location);
        assertTrue(location != null && location.startsWith(AUTH_SERVER_URL));

        // Step 2: Simulate user authentication at the auth server
        Response authResponse = given()
                .redirects().follow(false)
                .cookies(response.getCookies())
                .header(AUTHORIZATION, "Basic dXNlcjpwYXNzd29yZAo=")
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

    private static ConditionalInterceptor createConditionalInterceptorWithGroovy(String test, String groovy) {
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
        azureRouter.shutdown();
        nginxRouter.shutdown();
    }

}
