/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.client;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2client.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2ResourceErrorForwardingTest {

    protected final BrowserMock browser = new BrowserMock();
    private final int limit = 500;
    protected HttpRouter mockAuthServer;
    protected final ObjectMapper om = new ObjectMapper();
    final int serverPort = 3062;
    private final String serverHost = "localhost";
    private final int clientPort = 31337;
    private HttpRouter oauth2Resource;
    private final AtomicReference<String> error = new AtomicReference<>();
    private final AtomicReference<String> errorDescription = new AtomicReference<>();

    private String getServerAddress() {
        return "http://" + serverHost + ":" + serverPort;
    }

    protected String getClientAddress() {
        return "http://" + serverHost + ":" + clientPort;
    }

    @BeforeEach
    public void init() throws IOException {
        error.set(null);
        errorDescription.set(null);

        mockAuthServer = new HttpRouter();
        mockAuthServer.getTransport().setBacklog(10000);
        mockAuthServer.getTransport().setSocketTimeout(10000);
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(limit);
        mockAuthServer.getRuleManager().addProxyAndOpenPortIfNew(getMockAuthServiceProxy());
        mockAuthServer.start();

        oauth2Resource = new HttpRouter();
        oauth2Resource.getTransport().setBacklog(10000);
        oauth2Resource.getTransport().setSocketTimeout(10000);
        oauth2Resource.setHotDeploy(false);
        oauth2Resource.getTransport().setConcurrentConnectionLimitPerIp(limit);
        oauth2Resource.getRuleManager().addProxy(getErrorCaptor(), MANUAL);
        oauth2Resource.getRuleManager().addProxy(getConfiguredOAuth2Resource(), MANUAL);
        oauth2Resource.start();
    }

    @AfterEach
    public void done() {
        if (mockAuthServer != null)
            mockAuthServer.stop();
        if (oauth2Resource != null)
            oauth2Resource.stop();
    }


    @Test
    public void postOriginalRequest() throws Exception {
        Exchange exc = new Request.Builder().post(getClientAddress() + "/init").body("demobody").buildExchange();

        exc = browser.apply(exc);

        assertEquals("/error", exc.getRequest().getUri());
        assertEquals("DEMO-123", error.get());
        assertEquals("This is a demo error.", errorDescription.get());

        assertEquals(200, exc.getResponse().getStatusCode());

        assertEquals("{\"errorsCaptured\":true}", exc.getResponse().getBodyAsStringDecoded());
    }

    private ServiceProxy getMockAuthServiceProxy() throws IOException {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(serverPort), null, 99999);

        WellknownFile wkf = getWellknownFile();
        wkf.init();

        sp.getInterceptors().add(new AbstractInterceptor() {

            @Override
            public synchronized Outcome handleRequest(Exchange exc) {
                if (exc.getRequestURI().endsWith("/.well-known/openid-configuration")) {
                    exc.setResponse(Response.ok(wkf.getWellknown()).build());
                } else if (exc.getRequestURI().startsWith("/auth?")) {
                    Map<String, String> params;
                    try {
                        params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    } catch (URISyntaxException | IOException e) {
                        throw new RuntimeException(e);
                    }
                    exc.setResponse(new FormPostGenerator(getClientAddress() + "/oauth2callback")
                                    .withParameter("error", "DEMO-123")
                                    .withParameter("error_description", "This is a demo error.")
                                    .withParameter("state", params.get("state")).build());
                }

                if (exc.getResponse() == null)
                    exc.setResponse(Response.notFound().build());
                return Outcome.RETURN;
            }
        });

        return sp;
    }

    private @NotNull WellknownFile getWellknownFile() {
        WellknownFile wkf = new WellknownFile();

        wkf.setIssuer(getServerAddress());
        wkf.setAuthorizationEndpoint(getServerAddress() + "/auth");
        wkf.setTokenEndpoint(getServerAddress() + "/token");
        wkf.setUserinfoEndpoint(getServerAddress() + "/userinfo");
        wkf.setRevocationEndpoint(getServerAddress() + "/revoke");
        wkf.setJwksUri(getServerAddress() + "/certs");
        wkf.setSupportedResponseTypes("code token");
        wkf.setSupportedSubjectType("public");
        wkf.setSupportedIdTokenSigningAlgValues("RS256");
        wkf.setSupportedScopes("openid email profile");
        wkf.setSupportedTokenEndpointAuthMethods("client_secret_post");
        wkf.setSupportedClaims("sub email username");
        wkf.setSupportedResponseModes(Set.of("query", "fragment", "form_post"));
        return wkf;
    }

    private ServiceProxy getConfiguredOAuth2Resource() {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort), null, 99999);

        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = getoAuth2Resource2Interceptor();


        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (!exc.getRequest().getUri().contains("is-logged-in"))
                    return Outcome.CONTINUE;

                boolean isLoggedIn = oAuth2ResourceInterceptor.getSessionManager().getSession(exc).isVerified();

                exc.setResponse(Response.ok("{\"success\":" + isLoggedIn + "}").header(Header.CONTENT_TYPE, APPLICATION_JSON).build());
                return Outcome.RETURN;
            }
        });
        sp.getInterceptors().add(oAuth2ResourceInterceptor);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    return handleRequestInternal(exc);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public Outcome handleRequestInternal(Exchange exc) throws IOException {
                OAuth2AnswerParameters answer = OAuth2AnswerParameters.deserialize(String.valueOf(exc.getProperty(Exchange.OAUTH2)));
                String accessToken = answer.getAccessToken();
                Map<String, String> body = Map.of(
                        "accessToken", accessToken,
                        "path", exc.getRequestURI(),
                        "method", exc.getRequest().getMethod(),
                        "body", exc.getRequest().getBodyAsStringDecoded()
                );

                exc.setResponse(Response.ok(om.writeValueAsString(body)).build());
                return Outcome.RETURN;
            }
        });
        return sp;
    }

    private @NotNull OAuth2Resource2Interceptor getoAuth2Resource2Interceptor() {
        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = new OAuth2Resource2Interceptor();
        oAuth2ResourceInterceptor.setSessionManager(new InMemorySessionManager());
        oAuth2ResourceInterceptor.setAfterErrorUrl("/error");
        MembraneAuthorizationService auth = new MembraneAuthorizationService();
        auth.setSrc(getServerAddress());
        auth.setClientId("2343243242");
        auth.setClientSecret("3423233123123");
        auth.setScope("openid profile");
        oAuth2ResourceInterceptor.setAuthService(auth);

        oAuth2ResourceInterceptor.setLogoutUrl("/logout");

        var withOutValue = new LoginParameter();
        withOutValue.setName("login_hint");

        var withValue = new LoginParameter();
        withValue.setName("foo");
        withValue.setValue("bar");

        oAuth2ResourceInterceptor.setLoginParameters(List.of(
                withOutValue,
                withValue
        ));

        var aud = new RequireAuth();
        aud.setExpectedAud("asdf");
        aud.setOauth2(oAuth2ResourceInterceptor);
        return oAuth2ResourceInterceptor;
    }

    private ServiceProxy getErrorCaptor() {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort), null, 99999);
        Path path = new Path();
        path.setValue("/error");
        sp.setPath(path);


        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                Map<String, String> params;
                try {
                    params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
                error.set(params.get("error"));
                errorDescription.set(params.get("error_description"));

                exc.setResponse(Response.ok("{\"errorsCaptured\":true}").header(Header.CONTENT_TYPE, APPLICATION_JSON).build());
                return Outcome.RETURN;
            }
        });
        return sp;
    }

}
