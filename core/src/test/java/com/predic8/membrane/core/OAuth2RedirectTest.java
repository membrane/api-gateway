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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.LogInterceptor.Level.DEBUG;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static io.restassured.RestAssured.config;
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
                setScope("openid");
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
                setLocation("src/test/resources/oauth2/loginDialog/dialog");
                setConsentFile("src/test/resources/oauth2/consentFile.json");
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
        Map<String, String> cookies = new HashMap<>();

        // Step 1: Initial request to the client
        Response response = given()
                .redirects().follow(false)
                .when()
                .get(CLIENT_URL)
                .then()
                .statusCode(307)
                .extract().response();
        //noinspection CollectionAddAllCanBeReplacedWithConstructor
        cookies.putAll(response.getCookies());

        String location = response.getHeader("Location");
        System.out.println("location = " + location);
        assertTrue(location != null && location.startsWith(AUTH_SERVER_URL));

        // Step 2: Simulate user authentication at the auth server
        Response formRedirect = given()
                .redirects().follow(false)
                .cookies(cookies)
                .when()
                .get(location)
                .then()
                .statusCode(307)
                .extract().response();
        cookies.putAll(formRedirect.getCookies());

        String dialogLocation = formRedirect.getHeader("Location");
        System.out.println("dialogLocation = " + dialogLocation);
        assertTrue(dialogLocation != null && dialogLocation.startsWith("/"));

        // Step 3: Open login page
        Response formRequest = given()
                .redirects().follow(true)
                .cookies(cookies)
                .when()
                .get(AUTH_SERVER_URL + dialogLocation)
                .then()
                .statusCode(200)
                .extract().response();
        cookies.putAll(formRequest.getCookies());

        System.out.println("formRequest = " + formRequest.body().prettyPrint());

        // Step 4: Submit login
        Response formSubmit = given()
                .redirects().follow(false)
                .cookies(cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .formParam("username", "user")
                .formParam("password", "password")
                .when()
                .post(AUTH_SERVER_URL + dialogLocation)
                .then()
                .statusCode(200)
                .extract().response();
        cookies.putAll(formSubmit.getCookies());

        System.out.println("formSubmit.prettyPrint() = " + formSubmit.prettyPrint());
        String clientRedirect = formSubmit.getHeader("Location");
        System.out.println("clientRedirect = " + clientRedirect);
        assertTrue(clientRedirect != null && clientRedirect.startsWith(CLIENT_URL));

        // Step 4: Submit login
        Response formRedire = given()
                .redirects().follow(false)
                .cookies(cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .formParam("username", "user")
                .formParam("password", "password")
                .when()
                .post(AUTH_SERVER_URL)
                .then()
                .statusCode(307)
                .extract().response();
        cookies.putAll(formRedire.getCookies());

        System.out.println("formSubmit.prettyPrint() = " + formRedire.prettyPrint());
        String clientRedirect2 = formRedire.getHeader("Location");
        System.out.println("clientRedirect = " + clientRedirect2);
        //assertTrue(clientRedirect2 != null && clientRedirect2.startsWith(CLIENT_URL));

        // Step 4.5: Submit login2
        Response formRedire2 = given()
                .redirects().follow(false)
                .cookies(cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .formParam("username", "user")
                .formParam("password", "password")
                .when()
                .post(clientRedirect2)
                .then()
                .statusCode(307)
                .extract().response();
        cookies.putAll(formRedire2.getCookies());

        System.out.println("formRedire2.prettyPrint() = " + formRedire.prettyPrint());
        String clientRedirect3 = formRedire2.getHeader("Location");
        System.out.println("clientRedirect3 = " + clientRedirect3);
        assertTrue(clientRedirect3 != null && clientRedirect3.startsWith(CLIENT_URL));

        // Step 5: Make the authenticated POST request
        given()
                .cookies(cookies)
                .when()
                .post(clientRedirect3)
                .then()
                .statusCode(400);
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
