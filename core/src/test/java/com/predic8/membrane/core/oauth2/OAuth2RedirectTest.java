/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.oauth2;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.*;
import com.predic8.membrane.core.interceptor.oauth2client.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.test.*;
import io.restassured.response.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.interceptor.log.LogInterceptor.Level.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2RedirectTest {

    static Router authorizationServerRouter;
    static Router oauth2ResourceRouter;
    static Router backendRouter;
    static AtomicReference<String> firstUrlHit = new AtomicReference<>();
    static AtomicReference<String> targetUrlHit = new AtomicReference<>();
    static AtomicReference<String> interceptorChainHit = new AtomicReference<>();

    @BeforeEach
    void init() throws Exception {
        authorizationServerRouter = startProxyRule(getAuthorizationServerRule());
        oauth2ResourceRouter = startProxyRule(getOAuth2ResourceRule());
        backendRouter = startProxyRule(getBackendRule());
    }

    @AfterEach
    void shutdown() {
        authorizationServerRouter.shutdown();
        oauth2ResourceRouter.shutdown();
        backendRouter.shutdown();
    }

    @Test
    void testGet() {
        OAuth2AuthFlowClient OAuth2 = new OAuth2AuthFlowClient("http://localhost:2002");
        // Step 1: Initial request to the client
        Response clientResponse = OAuth2.step1originalRequestGET("/a?b=c&d=ä");
        // Step 2: Send to authentication at OAuth2 server
        String loginLocation = OAuth2.step2sendAuthToOAuth2Server(clientResponse);
        System.out.println("loginLocation = " + loginLocation);
        // Step 3: Open login page
        OAuth2.step3openLoginPage(loginLocation);
        // Step 4: Submit login
        OAuth2.step4submitLogin(loginLocation, "user", "password");
        // Step 5: Redirect to consent
        String consentLocation = OAuth2.step5redirectToConsent();
        // Step 6: Open consent dialog
        OAuth2.step6openConsentDialog(consentLocation);
        // Step 7: Submit consent
        OAuth2.step7submitConsent(consentLocation);
        // Step 8: Redirect back to client
        String callbackUrl = OAuth2.step8redirectToClient();
        // Step 9: Exchange Code for Token & continue original request.·
        OAuth2.step9exchangeCodeForToken(
                callbackUrl,
                "GET | null | "
                // method is 'GET', Content-Type is not set, body is empty
        );

        assertEquals(firstUrlHit.get(), targetUrlHit.get(), "Check that URL survived encoding.");
        assertEquals(firstUrlHit.get(), interceptorChainHit.get(), "Is interceptor chain correctly continued?");
    }

    @Test
    void testPost() {
        OAuth2AuthFlowClient OAuth2 = new OAuth2AuthFlowClient("http://localhost:2002");
        // Step 1: Initial request to the client
        Response clientResponse = OAuth2.step1originalRequestPOST("/a?b=c&d=ä");
        // Step 2: Send to authentication at OAuth2 server
        String loginLocation = OAuth2.step2sendAuthToOAuth2Server(clientResponse);
        // Step 3: Open login page
        OAuth2.step3openLoginPage(loginLocation);
        // Step 4: Submit login
        OAuth2.step4submitLogin(loginLocation, "user", "password");
        // Step 5: Redirect to consent
        String consentLocation = OAuth2.step5redirectToConsent();
        // Step 6: Open consent dialog
        OAuth2.step6openConsentDialog(consentLocation);
        // Step 7: Submit consent
        OAuth2.step7submitConsent(consentLocation);
        // Step 8: Redirect back to client
        String callbackUrl = OAuth2.step8redirectToClient();
        // Step 9: Exchange Code for Token & continue original request.·
        OAuth2.step9exchangeCodeForToken(
                callbackUrl,
                "POST | text/x-json; charset=ISO-8859-1 | [true]"
                // method is POST, Content-Type is 'text/x-json; charset=ISO-8859-1', body is '[true]'
        );

        assertTrue(targetUrlHit.get().startsWith(firstUrlHit.get()), "Check that URL survived encoding.");
        assertEquals(firstUrlHit.get(), interceptorChainHit.get(), "Is interceptor chain correctly continued?");
    }

    private static IfInterceptor createConditionalInterceptorWithReturnMessage(String test, String returnMessage) {
        return new IfInterceptor() {{
            setLanguage(SPEL);
            setTest(test);
            setInterceptors(List.of(
                new ResponseInterceptor() {{
                    setInterceptors(List.of(
                        new TemplateInterceptor() {{
                            setTextTemplate(returnMessage);
                        }}
                    ));
                }}
            ));
        }};
    }

    private static Router startProxyRule(SSLableProxy azureRule) throws Exception {
        Router router = new Router();
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.setTransport(new HttpTransport());
        router.getRuleManager().addProxyAndOpenPortIfNew(azureRule);
        router.init();
        return router;
    }

    private static @NotNull SSLableProxy getBackendRule() {
        SSLableProxy nginxRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2001), "localhost", 80);
        nginxRule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                targetUrlHit.set(exc.getRequest().getUri());
                return Outcome.CONTINUE;
            }
        });
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'POST'", "POST | ${exc.request.header.getFirstValue('Content-Type')} | ${exc.request.body}"));
        nginxRule.getInterceptors().add(createConditionalInterceptorWithReturnMessage("method == 'GET'", "GET | ${exc.request.header.getFirstValue('Content-Type')} | ${exc.request.body}"));
        nginxRule.getInterceptors().add(new ReturnInterceptor());
        return nginxRule;
    }

    private static @NotNull SSLableProxy getOAuth2ResourceRule() {
        SSLableProxy membraneRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2000), "localhost", 2001);
        membraneRule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
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
        membraneRule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (interceptorChainHit.get() == null)
                    interceptorChainHit.set(exc.getRequest().getUri());
                return Outcome.CONTINUE;
            }
        });
        return membraneRule;
    }

    private static @NotNull SSLableProxy getAuthorizationServerRule() {
        SSLableProxy azureRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 2002), "localhost", 80);
        azureRule.getInterceptors().add(new LogInterceptor() {{
            setLevel(DEBUG);
        }});
        azureRule.getInterceptors().add(new OAuth2AuthorizationServerInterceptor() {{
            setLocation(getPathFromResource("openId/dialog"));
            setConsentFile(getPathFromResource("openId/consentFile.json"));
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
