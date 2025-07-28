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

package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.client.*;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.util.*;
import org.jose4j.jwt.*;
import org.jose4j.jwt.consumer.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.oauth2.client.b2c.MockAuthorizationServer.SERVER_PORT;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2CallbackRequestHandler.MEMBRANE_MISSING_SESSION_DESCRIPTION;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.parseQueryString;
import static org.junit.jupiter.api.Assertions.*;

public abstract class OAuth2ResourceB2CTest {
    private final Logger LOG = LoggerFactory.getLogger(OAuth2ResourceB2CTest.class);
    private final B2CTestConfig tc = new B2CTestConfig();
    private final ObjectMapper om = new ObjectMapper();
    private final AtomicBoolean didLogIn = new AtomicBoolean();
    private final AtomicBoolean didLogOut = new AtomicBoolean();
    private final BrowserMock browser = new BrowserMock();
    private final MockAuthorizationServer mockAuthorizationServer = new MockAuthorizationServer(tc, () -> didLogIn.set(true), () -> didLogOut.set(true));
    private final B2CMembrane b2cMembrane = new B2CMembrane(tc, createSessionManager());

    @BeforeEach
    public void init() throws Exception {
        didLogIn.set(false);
        didLogOut.set(false);
        mockAuthorizationServer.resetBehavior();
        mockAuthorizationServer.init();
        b2cMembrane.init();
    }

    @AfterEach
    public void done() {
        mockAuthorizationServer.stop();
        b2cMembrane.stop();
    }

    @Test
    public void getOriginalRequest() throws Exception {
        var excCallResource = browser.apply(get(tc.getClientAddress() + "/init"));
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));
        assertEquals("", body2.get("body"));
        assertEquals("GET", body2.get("method"));
        assertTrue(didLogIn.get());
    }

    @Test
    public void postOriginalRequest() throws Exception {
        var excCallResource = browser.apply(new Request.Builder().post(tc.getClientAddress() + "/init").body("demobody"));
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class); // No
        assertEquals("/init", body2.get("path"));
        assertEquals("demobody", body2.get("body"));
        assertEquals("POST", body2.get("method"));
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    public void testUseRefreshTokenOnTokenExpiration() throws Exception {
        mockAuthorizationServer.expiresIn = 1;

        var excCallResource = browser.apply(get(tc.getClientAddress() + "/init"));
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));

        Set<String> accessTokens = new HashSet<>();
        runInParallel((cdl) -> parallelTestWorker(cdl, accessTokens), tc.limit);
        synchronized (accessTokens) {
            assertEquals(accessTokens.size(), tc.limit);
        }
    }

    private void runInParallel(Consumer<CountDownLatch> job, int threadCount) {
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            threadList.add(new Thread(() -> job.accept(cdl)));
        }
        threadList.forEach(Thread::start);
        threadList.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void parallelTestWorker(CountDownLatch cdl, Set<String> accessTokens) {
        try {
            cdl.countDown();
            cdl.await();

            String uuid = UUID.randomUUID().toString();
            var excCallResource2 = browser.apply(get(tc.getClientAddress() + "/api/" + uuid));

            var body = om.readValue(excCallResource2.getResponse().getBodyAsStringDecoded(), Map.class);
            String path = (String) body.get("path");
            assertEquals("/api/" + uuid, path);
            synchronized (accessTokens) {
                accessTokens.add((String) body.get("accessToken"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testStateAttack() throws Exception {
        AtomicReference<String> ref = new AtomicReference<>();
        AtomicInteger state = new AtomicInteger();
        // state 0: the attacker aborts the OAuth2 flow at the AS
        // state 1: the helpless user continues using the same link

        mockAuthorizationServer.getMockAuthServer().getTransport().getInterceptors().add(2, new AbstractInterceptor() {
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


        Exchange excCallResource = get(tc.getClientAddress() + "/malicious").buildExchange();
        LOG.debug("getting {}", excCallResource.getDestinations().getFirst());
        browser.apply(excCallResource); // will be aborted

        browser.clearCookies();

        // send the auth link to some helpless (other) user
        excCallResource = browser.apply(get("http://localhost:" + SERVER_PORT + ref.get()));

        assertEquals(400, excCallResource.getResponse().getStatusCode());

        String response = excCallResource.getResponse().getBodyAsStringDecoded();
        assertTrue(response.contains(MEMBRANE_MISSING_SESSION_DESCRIPTION));
    }

    private List<String> getLinesContaining(String haystackLines, String needle) {
        return Arrays.stream(haystackLines.split("\n")).filter(l -> l.contains(needle)).toList();
    }

    // this should avoid session fixation attacks
    @Test
    public void newSessionCookieAfterLogin() throws URISyntaxException {
        // this initializes the session, but does not login the user (because redirects are not followed)
        browser.applyWithoutRedirect(get(tc.getClientAddress() + "/init"));

        var ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));
        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        String cookiesBefore = browser.getCookiesText();
        assertEquals(1, getLinesContaining(cookiesBefore, "=true").size());

        // now actually log in
        browser.apply(get(tc.getClientAddress() + "/init"));

        ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));
        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        String cookiesAfter = browser.getCookiesText();
        assertEquals(1, getLinesContaining(cookiesAfter, "=true").size());

        // we assert: session id has changed
        assertNotEquals(
                getLinesContaining(cookiesBefore, "=true").get(0),
                getLinesContaining(cookiesAfter, "=true").get(0));
    }

    @Test
    public void logout() throws Exception {
        browser.apply(get(tc.getClientAddress() + "/init"));

        var ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        assertEquals(200, browser.apply(get(tc.getClientAddress() + "/api/")).getResponse().getStatusCode());

        browser.apply(get(tc.getClientAddress() + "/logout"));

        ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        assertEquals(0, browser.getCookieCount());
        assertTrue(didLogOut.get());

        // accessing the API triggers a login
        Response response = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api/")).getResponse();
        assertEquals(302, response.getStatusCode());
        assertTrue(response.getHeader().getFirstValue("Location").startsWith("http://localhost:" + SERVER_PORT + "/" + tc.tenantId + "/" + tc.susiFlowId));
    }

    @Test
    public void logoutClearsOldCookie() throws Exception {
        browser.apply(get(tc.getClientAddress() + "/init"));

        var ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        assertEquals(200, browser.apply(get(tc.getClientAddress() + "/api/")).getResponse().getStatusCode());

        Map<String, Map<String, String>> cookiesSnapshot = browser.createCookiesSnapshot();

        browser.apply(get(tc.getClientAddress() + "/logout"));

        browser.applyCookiesSnapshot(cookiesSnapshot);

        ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        assertEquals(1, browser.getCookieCount());
        assertTrue(didLogOut.get());

        // accessing the API triggers a login
        Response response = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api/")).getResponse();
        assertEquals(302, response.getStatusCode());
        assertTrue(response.getHeader().getFirstValue("Location").startsWith("http://localhost:" + SERVER_PORT + "/" + tc.tenantId + "/" + tc.susiFlowId));
    }

    @Test
    public void staleSessionLogout() throws Exception {
        var ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        // call to /logout uses cookieHandlingHttpClient: *NOT* following the redirect (which would auto-login again)
        browser.applyWithoutRedirect(get(tc.getClientAddress() + "/logout"));

        ili = browser.apply(get(tc.getClientAddress() + "/is-logged-in"));

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        assertEquals(0, browser.getCookieCount());
    }

    @Test
    public void requestAuth() throws Exception {
        var exc = browser.apply(get(tc.getClientAddress() + "/api/init"));

        assertEquals(200, exc.getResponse().getStatusCode());

        b2cMembrane.requireAuth.setExpectedAud(UUID.randomUUID().toString());
        var exc2 = browser.apply(get(tc.getClientAddress() + "/api/init"));

        assertTrue(exc2.getResponse().getStatusCode() >= 400);
    }

    @Test
    public void userFlowTest() throws Exception {
        var exc = browser.apply(get(tc.getClientAddress() + "/init"));

        assertNull(getAccessToken(exc));

        exc = browser.apply(get(tc.getClientAddress() + "/api/"));

        JwtClaims c2 = createJwtConsumer().processToClaims(getAccessToken(exc));
        assertEquals(12, c2.getClaimsMap().size());
        assertEquals("b2c_1_susi", c2.getClaimValue("tfp"));
    }

    @Test
    public void userFlowViaInitiatorTest() throws Exception {
        browser.apply(get(tc.getClientAddress() + "/init"));

        var exc = browser.apply(get(tc.getClientAddress() + "/pe/init"));

        assertNull(getAccessToken(exc));

        exc = browser.apply(get(tc.getClientAddress() + "/api/"));

        JwtClaims c2 = createJwtConsumer().processToClaims(getAccessToken(exc));
        assertEquals(11, c2.getClaimsMap().size());
        assertEquals("b2c_1_profile_editing", c2.getClaimValue("tfp"));
    }

    private String getAccessToken(Exchange exc) throws JsonProcessingException {
        return (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
    }

    @Test
    public void multipleUserFlowsTest() throws Exception {
        var exc = browser.apply(get(tc.getClientAddress() + "/init"));

        browser.apply(get(tc.getClientAddress() + "/pe2/init"));

        exc = browser.apply(get(tc.getClientAddress() + "/api/"));

        String a2 = getAccessToken(exc);
        JwtClaims c2 = createJwtConsumer().processToClaims(a2);
        assertEquals(11, c2.getClaimsMap().size());
        assertEquals("b2c_1_profile_editing2", c2.getClaimValue("tfp"));
    }

    @Test
    public void multipleUserFlowsWithErrorTest() throws Exception {
        browser.apply(get(tc.getClientAddress() + "/init"));

        mockAuthorizationServer.returnOAuth2ErrorFromSignIn.set(true);

        browser.apply(get(tc.getClientAddress() + "/pe2/init"));

        // here, an error is returned (as checked by {@link #errorDuringSignIn()}) and the user is logged out

        var exc = browser.apply(get(tc.getClientAddress() + "/api-no-auth-needed/"));

        String a2 = getAccessToken(exc);
        assertEquals("null", a2); // no access token, because user is logged out.
    }

    @Test
    public void stayLoggedInAfterProfileEditing2() throws Exception {
        browser.apply(get(tc.getClientAddress() + "/init"));

        mockAuthorizationServer.abortSignIn.set(true);

        // /pe2/init keeps the user logged in while sending her to the AS
        var exc = browser.apply(get(tc.getClientAddress() + "/pe2/init"));

        assertEquals("signin aborted", exc.getResponse().getBodyAsStringDecoded());

        exc = browser.apply(get(tc.getClientAddress() + "/api-no-auth-needed/"));

        // valid access token, since still logged in
        JwtClaims c2 = createJwtConsumer().processToClaims(getAccessToken(exc));
        assertEquals(11, c2.getClaimsMap().size());
        assertEquals("b2c_1_profile_editing2", c2.getClaimValue("tfp"));
    }

    @Test
    public void notLoggedInAfterProfileEditing() throws Exception {
        browser.apply(get(tc.getClientAddress() + "/init"));

        mockAuthorizationServer.abortSignIn.set(true);

        // /pe/init logs the user out before sending her to the AS
        var exc = browser.apply(get(tc.getClientAddress() + "/pe/init"));

        assertEquals("signin aborted", exc.getResponse().getBodyAsStringDecoded());

        exc = browser.apply(get(tc.getClientAddress() + "/api-no-auth-needed/"));

        String a2 = getAccessToken(exc);
        assertEquals("null", a2); // no access token, because user is logged out.
    }


    @Test
    public void loginParams() throws Exception {
        var exc = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/init?login_hint=def&illegal=true"));

        String location = exc.getResponse().getHeader().getFirstValue("Location");

        URI jUri = new URIFactory().create(location);
        String q = jUri.getRawQuery();

        var params = parseQueryString(q, ERROR);

        System.out.println(location);
        System.out.println(params);

        assertTrue(params.containsKey("foo"));
        assertEquals("bar", params.get("foo"));
        assertTrue(params.containsKey("login_hint"));
        assertEquals("def", params.get("login_hint"));
        assertFalse(params.containsKey("illegal"));
    }

    @Test
    public void loginParamsPerFlow() throws Exception {
        var exc = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/pe/init?domain_hint=flow&illegal=true"));

        var params = parseQueryString(new URIFactory().create(exc.getResponse().getHeader().getLocation()).getRawQuery(), ERROR);

        assertTrue(params.containsKey("fooflow"));
        assertEquals("bar", params.get("foo"));
        assertTrue(params.containsKey("domain_hint"));
        assertEquals("flow", params.get("domain_hint"));
        assertFalse(params.containsKey("illegal"));
    }

    @Test
    public void loginNotRequired() throws Exception {
        // access 1: not authenticated, expecting no token
        var exc = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api-no-auth-needed/"));

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        var res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertEquals("null", res.get("accessToken"));

        browser.apply(get(tc.getClientAddress() + "/pe/init"));

        // access 2: authenticated, expecting JWT
        exc = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api-no-auth-needed/"));
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertTrue(((String)res.get("accessToken")).startsWith("eyJ"));
    }

    @Test
    public void returning4xx() throws Exception {
        // access 1: not authenticated, expecting 4xx
        var exc = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api-no-redirect/"));

        assertEquals(403, exc.getResponse().getStatusCode());
        assertEquals("Forbidden", exc.getResponse().getStatusMessage());
        assertNull(exc.getResponse().getHeader().getFirstValue(SET_COOKIE));

        browser.apply(get(tc.getClientAddress() + "/pe/init"));

        // access 2: authenticated, expecting JWT
        exc = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api-no-redirect/"));
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        var res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertTrue(((String)res.get("accessToken")).startsWith("eyJ"));
    }

    @Test
    public void requireAuthRedirects() throws Exception {
        var excCallResource = browser.applyWithoutRedirect(get(tc.getClientAddress() + "/api/"));
        assertEquals(302, excCallResource.getResponse().getStatusCode());
        assertTrue(excCallResource.getResponse().getHeader().getFirstValue(Header.LOCATION).contains(
                ":"+mockAuthorizationServer.getServerPort()+"/"));
        assertFalse(didLogIn.get());
    }

    @Test
    public void errorDuringSignIn() throws Exception {
        mockAuthorizationServer.returnOAuth2ErrorFromSignIn.set(true);
        var exc = browser.apply(get(tc.getClientAddress() + "/api/"));

        ProblemDetails pd = ProblemDetails.parse(exc.getResponse());
        assertEquals("https://membrane-api.io/problems/security", pd.getType());
        assertEquals("DEMO-123", pd.getInternal().get("error"));
    }

    @Test
    public void api1and2() throws Exception {
        var exc = browser.apply(get(tc.getClientAddress() + "/api/"));
        Map body = om.readValue(exc.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/api/", body.get("path"));
        assertEquals("", body.get("body"));
        assertEquals("GET", body.get("method"));
        assertTrue(((String)body.get("accessToken")).startsWith("eyJ"));

        exc = browser.apply(get(tc.getClientAddress() + "/api2/"));
        body = om.readValue(exc.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/api2/", body.get("path"));
        assertEquals("", body.get("body"));
        assertEquals("GET", body.get("method"));
        assertTrue(((String)body.get("accessToken")).startsWith("eyJ"));
    }

    protected abstract SessionManager createSessionManager();

    private JwtConsumer createJwtConsumer() {
        return new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setVerificationKey(mockAuthorizationServer.getPublicKey())
                .setExpectedAudience(true, tc.api1Id).build();
    }
}
