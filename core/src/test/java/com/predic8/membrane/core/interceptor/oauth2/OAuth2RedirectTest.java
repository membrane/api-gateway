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
package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.IfInterceptor;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.oauth2client.SessionOriginalExchangeStore;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.proxies.SSLableProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.test.OAuth2AuthFlowClient;
import io.restassured.filter.log.LogDetail;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePorts;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class OAuth2RedirectTest {

    URI authServerBaseUrl;
    URI backendBaseUrl;
    URI clientBaseUrl;
    DefaultRouter authorizationServerRouter;
    DefaultRouter oauth2ResourceRouter;
    DefaultRouter backendRouter;
    final AtomicReference<String> firstUrlHit = new AtomicReference<>();
    final AtomicReference<String> targetUrlHit = new AtomicReference<>();
    final AtomicReference<String> interceptorChainHit = new AtomicReference<>();

    @BeforeEach
    void init() throws Exception {
        int[] ports = freePorts(3);
        authServerBaseUrl = URI.create("http://localhost:" + ports[0]);
        backendBaseUrl = URI.create("http://localhost:" + ports[1]);
        clientBaseUrl = URI.create("http://localhost:" + ports[2]);
        authorizationServerRouter = new DefaultRouter();
        oauth2ResourceRouter = new DefaultRouter();
        backendRouter = new DefaultRouter();
        startProxyRule(authorizationServerRouter, getAuthorizationServerRule());
        startProxyRule(oauth2ResourceRouter, getOAuth2ResourceRule());
        startProxyRule(backendRouter, getBackendRule());
    }

    @AfterEach
    void shutdown() {
        if (authorizationServerRouter != null) authorizationServerRouter.stop();
        if (oauth2ResourceRouter != null) oauth2ResourceRouter.stop();
        if (backendRouter != null) backendRouter.stop();
    }

    @Test
    void invalidState() {
        given()
            .redirects().follow(false)
            //.cookies(memCookies) TODO: create clone of this test with existing session
        .when()
            .get(clientBaseUrl + "/oauth2callback?code=b2296nh0navopaj5iq5slu7dje&state=security_token=miv38g9f80v7fiau029ctfel2o&url=/a?b=c&d=%C3%A4")
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .statusCode(400)
            .body(is("Missing session."));
    }

    @Test
    void testGet() throws Exception {
        OAuth2AuthFlowClient OAuth2 = getOAuth2AuthFlowClient();
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
        var callback = OAuth2.step8redirectToClient();
        // Step 9: Exchange Code for Token & continue original request.·
        var redirectUrl = OAuth2.step9executeCallback(callback);

        OAuth2.step10callOriginalUrl(
                redirectUrl,
                "GET | null | "
                // method is 'GET', Content-Type is not set, body is empty
        );

        assertEquals(firstUrlHit.get(), targetUrlHit.get(), "Check that URL survived encoding.");
        assertEquals(firstUrlHit.get(), interceptorChainHit.get(), "Is interceptor chain correctly continued?");
    }

    abstract OAuth2AuthFlowClient getOAuth2AuthFlowClient();

    @Test
    void testPost() throws Exception {
        OAuth2AuthFlowClient OAuth2 = getOAuth2AuthFlowClient();
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
        var callback = OAuth2.step8redirectToClient();
        // Step 9: Exchange Code for Token & continue original request.·
        var redirectUrl = OAuth2.step9executeCallback(callback);

        OAuth2.step10callOriginalUrl(
                redirectUrl,
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
            setFlow(List.of(
                new ResponseInterceptor() {{
                    setFlow(List.of(
                        new TemplateInterceptor() {{
                            setSrc(returnMessage);
                        }}
                    ));
                }}
            ));
        }};
    }

    private static void startProxyRule(DefaultRouter router, SSLableProxy azureRule) throws Exception {
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.setTransport(new HttpTransport());
        router.add(azureRule);
        router.start();
    }

    private @NotNull SSLableProxy getBackendRule() {
        SSLableProxy nginxRule = new ServiceProxy(new ServiceProxyKey(backendBaseUrl.getHost(), "*", ".*", backendBaseUrl.getPort()), "localhost", 80);
        nginxRule.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                targetUrlHit.set(exc.getRequest().getUri());
                return CONTINUE;
            }
        });
        nginxRule.getFlow().add(createConditionalInterceptorWithReturnMessage("method == 'POST'", "POST | ${exc.request.header.getFirstValue('Content-Type')} | ${exc.request.body}"));
        nginxRule.getFlow().add(createConditionalInterceptorWithReturnMessage("method == 'GET'", "GET | ${exc.request.header.getFirstValue('Content-Type')} | ${exc.request.body}"));
        nginxRule.getFlow().add(new ReturnInterceptor());
        return nginxRule;
    }

    private @NotNull SSLableProxy getOAuth2ResourceRule() {
        SSLableProxy membraneRule = new ServiceProxy(new ServiceProxyKey(clientBaseUrl.getHost(), "*", ".*", clientBaseUrl.getPort()), backendBaseUrl.getHost(), backendBaseUrl.getPort());
        membraneRule.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (firstUrlHit.get() == null)
                    firstUrlHit.set(exc.getRequest().getUri());
                return CONTINUE;
            }
        });
        membraneRule.getFlow().add(new OAuth2Resource2Interceptor() {{
            setSessionManager(new InMemorySessionManager());
            setAuthService(new MembraneAuthorizationService() {{
                setSrc(authServerBaseUrl.toString());
                setClientId("abc");
                setClientSecret("def");
                setScope("openid profile");
            }});
            setOriginalExchangeStore(new SessionOriginalExchangeStore());
        }});
        membraneRule.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (interceptorChainHit.get() == null)
                    interceptorChainHit.set(exc.getRequest().getUri());
                return CONTINUE;
            }
        });
        return membraneRule;
    }
    abstract @NotNull SSLableProxy getAuthorizationServerRule();
}
