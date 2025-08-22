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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.google.code.yanf4j.util.ConcurrentHashSet;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2client.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2CallbackRequestHandler.MEMBRANE_MISSING_SESSION_DESCRIPTION;
import static org.junit.jupiter.api.Assertions.*;

public abstract class OAuth2ResourceTest {

    protected final BrowserMock browser = new BrowserMock();
    private final int limit = 200;
    protected HttpRouter mockAuthServer;
    protected final ObjectMapper om = new ObjectMapper();
    final Logger LOG = LoggerFactory.getLogger(OAuth2ResourceTest.class);
    final int serverPort = 3062;
    private final String serverHost = "localhost";
    private final int clientPort = 31337;
    private HttpRouter oauth2Resource;

    private String getServerAddress() {
        return "http://" + serverHost + ":" + serverPort;
    }

    protected String getClientAddress() {
        return "http://" + serverHost + ":" + clientPort;
    }

    @BeforeEach
    public void init() throws IOException {
        mockAuthServer = new HttpRouter();
        mockAuthServer.getTransport().setBacklog(10000);
        mockAuthServer.getTransport().setSocketTimeout(10000);
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(limit+1);
        mockAuthServer.getRuleManager().addProxyAndOpenPortIfNew(getMockAuthServiceProxy());
        mockAuthServer.start();

        oauth2Resource = new HttpRouter();
        oauth2Resource.getTransport().setBacklog(10000);
        oauth2Resource.getTransport().setSocketTimeout(10000);
        oauth2Resource.setHotDeploy(false);
        oauth2Resource.getTransport().setConcurrentConnectionLimitPerIp(limit+1);
        oauth2Resource.getRuleManager().addProxyAndOpenPortIfNew(getConfiguredOAuth2Resource());
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
    public void getOriginalRequest() throws Exception {
        var response = browser.apply(get(getClientAddress() + "/init")).getResponse();
        assertEquals(200, response.getStatusCode());
        var body = om.readValue(response.getBodyAsStream(), new TypeReference<Map<String, String>>() {});
        assertEquals("/init", body.get("path"));
        assertEquals("", body.get("body"));
        assertEquals("GET", body.get("method"));
    }

    @Test
    public void postOriginalRequest() throws Exception {
        var response = browser.apply(post(getClientAddress() + "/init").body("demobody")).getResponse();
        assertEquals(200, response.getStatusCode());
        var body = om.readValue(response.getBodyAsStream(), new TypeReference<Map<String, String>>() {});
        assertEquals("/init", body.get("path"));
        assertEquals("demobody", body.get("body"));
        assertEquals("POST", body.get("method"));
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    void testUseRefreshTokenOnTokenExpiration() throws Exception {
        var response = browser.apply(get(getClientAddress() + "/init")).getResponse();
        assertEquals(200, response.getStatusCode());
        var body = om.readValue(response.getBodyAsStream(), new TypeReference<Map<String, String>>() {});
        assertEquals("/init", body.get("path"));

        CountDownLatch cdl = new CountDownLatch(limit);
        Set<String> accessTokens = new ConcurrentHashSet<>();
        IntStream.range(0, limit)
                .mapToObj(i -> getStartedThread(() -> accessTokens.add(getToken(cdl))))
                .toList() // required to avoid stream execution deadlocking on CountDownLatch.await()
                .forEach(OAuth2ResourceTest::joinThread);
        assertEquals(limit, accessTokens.size());
    }

    private String getToken(CountDownLatch cdl) {
        try {
            cdl.countDown();
            cdl.await();
            String path = "/" + UUID.randomUUID();
            var response = browser.apply(get(getClientAddress() + path)).getResponse();
            var body = om.readValue(response.getBodyAsStringDecoded(), new TypeReference<Map<String, String>>() {});
            assertEquals(path, body.get("path"));
            return body.get("accessToken");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Thread getStartedThread(Runnable runnable) {
        var thread = new Thread(runnable);
        thread.start();
        return thread;
    }

    protected static void joinThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testStateAttack() throws Exception {
        AtomicReference<String> ref = new AtomicReference<>();
        AtomicInteger state = new AtomicInteger();
        // state 0: the attacker aborts the OAuth2 flow at the AS
        // state 1: the helpless user continues using the same link

        mockAuthServer.getTransport().getInterceptors().add(2, new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (state.get() == 0) {
                    ref.set(exc.getOriginalRequestUri());
                    state.set(1);
                    exc.setResponse(Response.internalServerError().body("").build());
                    return Outcome.RETURN;
                }

                return super.handleRequest(exc);
            }
        });


        var malicious = get(getClientAddress() + "/malicious").buildExchange();
        LOG.debug("getting {}", malicious.getDestinations().getFirst());
        browser.apply(malicious); // will be aborted

        browser.clearCookies(); // send the auth link to some helpless (other) user

        var response = browser.apply(get("http://localhost:" + serverPort + ref.get())).getResponse();
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBodyAsStringDecoded().contains(MEMBRANE_MISSING_SESSION_DESCRIPTION));
    }

    @Test
    public void testCSRFProblem() throws Exception {
        AtomicBoolean blocked = new AtomicBoolean(true);
        mockAuthServer.getTransport().getInterceptors().add(2, new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (blocked.get()) {
                    exc.setResponse(Response.ok("Login aborted").build());
                    return Outcome.RETURN;
                }
                return Outcome.CONTINUE;
            }
        });

        // hit the client, do not continue at AS with login
        var response = browser.apply(get(getClientAddress() + "/init" + 0)).getResponse();

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBodyAsStringDecoded().contains("Login aborted"));

        blocked.set(false);

        // hit client again, login
        var secondResponse = browser.apply(get(getClientAddress() + "/init" + 1)).getResponse();

        // works
        assertEquals(200, secondResponse.getStatusCode());
        assertTrue(secondResponse.getBodyAsStringDecoded().contains("/init1"));
    }

    @Test
    public void logout() throws Exception {
        browser.apply(get(getClientAddress() + "/init"));

        var isLoggedInResponse = browser.apply(get(getClientAddress() + "/is-logged-in")).getResponse();
        assertEquals(200, isLoggedInResponse.getStatusCode());
        assertTrue(isLoggedInResponse.getBodyAsStringDecoded().contains("true"));

        // call to /logout uses cookieHandlingHttpClient: *NOT* following the redirect (which would auto-login again)
        browser.applyWithoutRedirect(get(getClientAddress() + "/logout"));

        var secondIsLoggedInResponse = browser.apply(get(getClientAddress() + "/is-logged-in")).getResponse();
        assertEquals(200, secondIsLoggedInResponse.getStatusCode());
        assertTrue(secondIsLoggedInResponse.getBodyAsStringDecoded().contains("false"));
    }

    @Test
    public void loginParams() throws Exception {
        var response = browser.applyWithoutRedirect(get(getClientAddress() + "/init?login_hint=def&illegal=true")).getResponse();
        assertEquals(302, response.getStatusCode());

        var params = extractQueryParameters(response.getHeader().getFirstValue("Location"));

        assertTrue(params.containsKey("foo"));
        assertEquals("bar", params.get("foo"));
        assertTrue(params.containsKey("login_hint"));
        assertEquals("def", params.get("login_hint"));
        assertFalse(params.containsKey("illegal"));
    }

    private static @NotNull Map<String, String> extractQueryParameters(String urlString) throws URISyntaxException {
        String rawQuery = new URIFactory().create(urlString).getRawQuery();
        return URLParamUtil.parseQueryString(rawQuery, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
    }


    private ServiceProxy getMockAuthServiceProxy() throws IOException {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(serverPort), null, 99999);

        WellknownFile wkf = getWellknownFile();
        wkf.init();

        sp.getInterceptors().add(new AbstractInterceptor() {

            final SecureRandom rand = new SecureRandom();

            @Override
            public synchronized Outcome handleRequest(Exchange exc) {
                try {
                    exc.setResponse(handleRequestInternal(exc));
                    return Outcome.RETURN;
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public synchronized Response handleRequestInternal(Exchange exc) throws URISyntaxException, IOException {
                if (exc.getRequestURI().endsWith("/.well-known/openid-configuration")) {
                    return Response.ok(wkf.getWellknown()).build();
                } else if (exc.getRequestURI().startsWith("/auth?")) {
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    return new FormPostGenerator(getClientAddress() + "/oauth2callback")
                        .withParameter("state", params.get("state"))
                        .withParameter("code", params.get("1234"))
                        .build();
                } else if (exc.getRequestURI().startsWith("/token")) {
                    ObjectMapper om = new ObjectMapper();
                    var responseData = Map.ofEntries(
                            Map.entry("access_token", new BigInteger(130, rand).toString(32)),
                            Map.entry("token_type", "bearer"),
                            Map.entry("expires_in", "1"),
                            Map.entry("refresh_token", new BigInteger(130, rand).toString(32))
                    );
                    return Response.ok(om.writeValueAsString(responseData)).contentType(APPLICATION_JSON).build();
                } else if (exc.getRequestURI().startsWith("/userinfo")) {
                    ObjectMapper om = new ObjectMapper();
                    Map<String, String> res = Map.of("username", "dummy");
                    return Response.ok(om.writeValueAsString(res)).contentType(APPLICATION_JSON).build();
                } else {
                    return Response.notFound().build();
                }
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

        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = new OAuth2Resource2Interceptor();
        configureSessionManager(oAuth2ResourceInterceptor);
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

    protected abstract void configureSessionManager(OAuth2Resource2Interceptor oauth2);
}
