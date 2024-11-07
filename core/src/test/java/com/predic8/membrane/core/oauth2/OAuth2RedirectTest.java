package com.predic8.membrane.core.oauth2;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.interceptor.LogInterceptor.Level.DEBUG;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static com.predic8.membrane.core.oauth2.OAuth2AuthFlowClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OAuth2RedirectTest {

    static Router azureRouter;
    static Router membraneRouter;
    static Router nginxRouter;
    static AtomicReference<String> firstUrlHit = new AtomicReference<>();
    static AtomicReference<String> targetUrlHit = new AtomicReference<>();

    @BeforeAll
    static void setup() throws Exception {
        azureRouter = startProxyRule(getAzureRule());
        membraneRouter = startProxyRule(getMembraneRule());
        nginxRouter = startProxyRule(getNginxRule());
    }

    @Test
    void testGet() {
        // Step 1: Initial request to the client
        Response clientResponse = step1originalRequest();

        // Step 2: Send to authentication at OAuth2 server
        String loginLocation = step2sendAuthToOAuth2Server(clientResponse);

        // Step 3: Open login page
        step3openLoginPage(loginLocation);

        // Step 4: Submit login
        step4submitLogin(loginLocation);

        // Step 5: Redirect to consent
        String consentLocation = step5redirectToConsent();

        // Step 6: Open consent dialog
        step6openConsentDialog(consentLocation);

        // Step 7: Submit consent
        step7submitConsent(consentLocation);

        // Step 8: Redirect back to client
        String callbackUrl = step8redirectToClient();

        // Step 9: Exchange Code for Token
        step9exchangeCodeForToken(callbackUrl);

        // Step 10: Make the authenticated POST request
        step10makeAuthPostRequest();

        assertEquals(firstUrlHit.get(), targetUrlHit.get(), "Check that URL survived encoding.");
    }

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
        nginxRule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                targetUrlHit.set(exc.getRequest().getUri());
                return Outcome.CONTINUE;
            }
        });
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'POST'", "POST"));
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'GET'", "GET"));
        nginxRule.getInterceptors().add(new ReturnInterceptor());
        return nginxRule;
    }

    private static @NotNull Rule getMembraneRule() {
        Rule membraneRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), "localhost", 2001);
        membraneRule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                if (firstUrlHit.get() == null)
                    firstUrlHit.set(exc.getRequest().getUri());
                return Outcome.CONTINUE;
            }
        });
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
