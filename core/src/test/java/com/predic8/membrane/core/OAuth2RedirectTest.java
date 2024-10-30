package com.predic8.membrane.core;

import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor;
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
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
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
import static io.restassured.RestAssured.given;
import static org.apache.http.HttpHeaders.LOCATION;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

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
            }});
            setOriginalExchangeStore(new SessionOriginalExchangeStore());
        }});

        Rule azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2002),  "localhost", 80);
        azureRule.getInterceptors().add(new LogInterceptor() {{
            setLevel(DEBUG);
        }});
        azureRule.getInterceptors().add(
            new OAuth2AuthorizationServerInterceptor() {{
                setLocation("src/test/resources/openId/dialog");
                setConsentFile("src/test/resources/openId/consentFile.json");
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

        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2001), "localhost", 80);
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'POST'", "POST"));
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'GET'", "GET"));
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

    // @formatter:off
    @Test
    void testGet() {
        Map<String, String> cookies = new HashMap<>();
        Map<String, String> memCookies = new HashMap<>();

        // Step 1: Initial request to the client
        Response response =
            given()
                .redirects().follow(false)
            .when()
                .get(CLIENT_URL)
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern(AUTH_SERVER_URL + ".*"))
                .extract().response();
        //noinspection CollectionAddAllCanBeReplacedWithConstructor
        memCookies.putAll(response.getCookies());

        // Step 2: Send to authentication at OAuth2 server
        Response formRedirect =
            given()
                .redirects().follow(false)
                .cookies(cookies)
                .urlEncodingEnabled(false)
            .when()
                .get(response.getHeader(LOCATION))
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern("/login.*"))
                .extract().response();
        cookies.putAll(formRedirect.getCookies());

        // Step 3: Open login page
        Response formRequest =
            given()
                .redirects().follow(true)
                .cookies(cookies)
            .when()
                .get(AUTH_SERVER_URL + formRedirect.getHeader(LOCATION))
            .then()
                .statusCode(200)
                .extract().response();
        cookies.putAll(formRequest.getCookies());

        // Step 4: Submit login
        Response formSubmit =
            given()
                .redirects().follow(false)
                .cookies(cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .formParam("username", "user")
                .formParam("password", "password")
            .when()
                .post(AUTH_SERVER_URL + formRedirect.getHeader(LOCATION))
            .then()
                .statusCode(200)
                .header(LOCATION, "/")
                .extract().response();
        cookies.putAll(formSubmit.getCookies());

        // Step 5: Redirect to consent
        Response consentRedirect =
            given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .get(AUTH_SERVER_URL)
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern("/login/consent.*"))
                .extract().response();
        cookies.putAll(consentRedirect.getCookies());

        // Step 6: Open consent dialog
        Response consentDialog =
            given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .get(AUTH_SERVER_URL + consentRedirect.getHeader(LOCATION))
            .then()
                .statusCode(200)
                .extract().response();
        cookies.putAll(consentDialog.getCookies());

        // Step 7: Submit consent
        Response consentSubmit =
            given()
                .redirects().follow(false)
                .cookies(cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Charset", "UTF-8")
                .formParam("consent", "Accept")
            .when()
                .post(AUTH_SERVER_URL + consentRedirect.getHeader(LOCATION))
            .then()
                .statusCode(200)
                .header(LOCATION, "/")
                .extract().response();
        cookies.putAll(consentSubmit.getCookies());

        // Step 8: Redirect back to client
        Response clientRedirect =
            given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .post(AUTH_SERVER_URL)
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern(CLIENT_URL + ".*"))
                .extract().response();

        // Step 9: Exchange Code for Token
        given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .post(clientRedirect.getHeader(LOCATION))
        .then()
            .statusCode(307)
            .header(LOCATION, "/")
            .extract().response();

        // Step 10: Make the authenticated POST request
        given()
            .cookies(memCookies)
        .when()
            .post(CLIENT_URL)
        .then()
            .body(equalToIgnoringCase("get"));
    }
    // @formatter:on

    private static ConditionalInterceptor createConditionalInterceptorWithReturnMessage(String test, String returnMessage) {
        return new ConditionalInterceptor() {{
            setLanguage(SPEL);
            setTest(test);
            setInterceptors(List.of(new StaticInterceptor() {{
                setTextTemplate(returnMessage);
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
