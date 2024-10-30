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
import org.jetbrains.annotations.NotNull;
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

    static Router azureRouter;
    static Router membraneRouter;
    static Router nginxRouter;

    @BeforeAll
    static void setup() throws Exception {
        Rule azureRule = getAzureRule();
        Rule membraneRule = getMembraneRule();
        Rule nginxRule = getNginxRule();

        azureRouter = startProxyRule(azureRule);
        membraneRouter = startProxyRule(membraneRule);
        nginxRouter = startProxyRule(nginxRule);
    }

    private static final String CLIENT_URL = "http://localhost:2000";

    private static final String AUTH_SERVER_URL = "http://localhost:2002";


    // @formatter:off
    @Test
    void testGet() {
        Map<String, String> cookies = new HashMap<>();
        Map<String, String> memCookies = new HashMap<>();

        // Step 1: Initial request to the client
        Response clientResponse = step1originalRequest(memCookies);

        // Step 2: Send to authentication at OAuth2 server
        Response formRedirect = step2sendAuthToOAuth2Server(cookies, clientResponse);

        // Step 3: Open login page
        step3openLoginPage(cookies, formRedirect);

        // Step 4: Submit login
        step4submitLogin(cookies, formRedirect);

        // Step 5: Redirect to consent
        Response consentRedirect = step5redirectToConsent(cookies);

        // Step 6: Open consent dialog
        step6openConsentDialog(cookies, consentRedirect);

        // Step 7: Submit consent
        step7submitConsent(cookies, consentRedirect);

        // Step 8: Redirect back to client
        Response clientRedirect = step8redirectToClient(cookies);

        // Step 9: Exchange Code for Token
        step9exchangeCodeForToken(memCookies, clientRedirect);

        // Step 10: Make the authenticated POST request
        step10makeAuthPostRequest(memCookies);
    }

    private static void step10makeAuthPostRequest(Map<String,String> memCookies) {
        given()
            .cookies(memCookies)
        .when()
            .post(CLIENT_URL)
        .then()
            .body(equalToIgnoringCase("get"));
    }

    private static void step9exchangeCodeForToken(Map<String,String> memCookies, Response clientRedirect) {
        given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .post(clientRedirect.getHeader(LOCATION))
        .then()
            .statusCode(307)
            .header(LOCATION, "/")
            .extract().response();
    }

    private static Response step8redirectToClient(Map<String,String> cookies) {
        return given()
                    .redirects().follow(false)
                    .cookies(cookies)
                .when()
                    .post(AUTH_SERVER_URL)
                .then()
                    .statusCode(307)
                    .header(LOCATION, matchesPattern(CLIENT_URL + ".*"))
                    .extract().response();
    }

    private static void step7submitConsent(Map<String,String> cookies, Response consentRedirect) {
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
    }

    private static void step6openConsentDialog(Map<String,String> cookies, Response consentRedirect) {
        given()
            .redirects().follow(false)
            .cookies(cookies)
        .when()
            .get(AUTH_SERVER_URL + consentRedirect.getHeader(LOCATION))
        .then()
            .statusCode(200)
            .extract().response();
    }

    private static Response step5redirectToConsent(Map<String,String> cookies) {
        return given()
                    .redirects().follow(false)
                    .cookies(cookies)
                .when()
                    .get(AUTH_SERVER_URL)
                .then()
                    .statusCode(307)
                    .header(LOCATION, matchesPattern("/login/consent.*"))
                    .extract().response();
    }

    private static void step4submitLogin(Map<String,String> cookies, Response formRedirect) {
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
    }

    private static void step3openLoginPage(Map<String,String> cookies, Response formRedirect) {
        given()
            .redirects().follow(true)
            .cookies(cookies)
        .when()
            .get(AUTH_SERVER_URL + formRedirect.getHeader(LOCATION))
        .then()
            .statusCode(200)
            .extract().response();
    }

    private static @NotNull Response step2sendAuthToOAuth2Server(Map<String,String> cookies, Response response) {
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
        return formRedirect;
    }

    private static @NotNull Response step1originalRequest(Map<String,String> memCookies) {
        Response response =
            given()
                .redirects().follow(false)
            .when()
                .get(CLIENT_URL)
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern(AUTH_SERVER_URL + ".*"))
                .extract().response();
            memCookies.putAll(response.getCookies());
        return response;
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

    private static Router startProxyRule(Rule azureRule) throws Exception {
        Router router = new Router();
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.setTransport(new HttpTransport());
        router.getRuleManager().addProxyAndOpenPortIfNew(azureRule);
        router.init();
        return router;
    }

    private static @NotNull Rule getNginxRule() {
        Rule nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2001), "localhost", 80);
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'POST'", "POST"));
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'GET'", "GET"));
        nginxRule.getInterceptors().add(new ReturnInterceptor());
        return nginxRule;
    }

    private static @NotNull Rule getMembraneRule() {
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
        return membraneRule;
    }

    private static @NotNull Rule getAzureRule() {
        Rule azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2002), "localhost", 80);
        azureRule.getInterceptors().add(new LogInterceptor() {{
            setLevel(DEBUG);
        }});
        azureRule.getInterceptors().add(new OAuth2AuthorizationServerInterceptor() {{
            setLocation("src/test/resources/openId/dialog");
            setConsentFile("src/test/resources/openId/consentFile.json");
            setTokenGenerator(new BearerTokenGenerator());
            setIssuer("http://localhost:2002");
            setUserDataProvider(new StaticUserDataProvider() {{
                setUsers(List.of(new User() {{
                    setUsername("user");
                    setPassword("password");
                }}));
            }});
            setClientList(new StaticClientList() {{
                setClients(List.of(new Client() {{
                    setClientId("abc");
                    setClientSecret("def");
                    setCallbackUrl("http://localhost:2000/oauth2callback");
                }}));
            }});
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
        }});
        return azureRule;
    }
}
