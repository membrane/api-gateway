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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
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
        Exchange excCallResource = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();

        excCallResource = browser.apply(excCallResource);
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));
        assertEquals("", body2.get("body"));
        assertEquals("GET", body2.get("method"));
        assertTrue(didLogIn.get());
    }

    @Test
    public void postOriginalRequest() throws Exception {
        Exchange excCallResource = new Request.Builder().post(tc.getClientAddress() + "/init").body("demobody").buildExchange();

        excCallResource = browser.apply(excCallResource);
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class); // No
        assertEquals("/init", body2.get("path"));
        assertEquals("demobody", body2.get("body"));
        assertEquals("POST", body2.get("method"));
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    public void testUseRefreshTokenOnTokenExpiration() throws Exception {
        mockAuthorizationServer.expiresIn = 1;

        Exchange excCallResource = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();

        excCallResource = browser.apply(excCallResource);
        var body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));

        Set<String> accessTokens = new HashSet<>();
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(tc.limit);
        for (int i = 0; i < tc.limit; i++) {
            threadList.add(new Thread(() -> {
                try {
                    cdl.countDown();
                    cdl.await();
                    String uuid = UUID.randomUUID().toString();
                    Exchange excCallResource2 = new Request.Builder().get(tc.getClientAddress() + "/api/" + uuid).buildExchange();
                    excCallResource2 = browser.apply(excCallResource2);
                    var body = om.readValue(excCallResource2.getResponse().getBodyAsStringDecoded(), Map.class);
                    String path = (String) body.get("path");
                    assertEquals("/api/" + uuid, path);
                    synchronized (accessTokens) {
                        accessTokens.add((String) body.get("accessToken"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));

        }
        threadList.forEach(Thread::start);
        threadList.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        synchronized (accessTokens) {
            assertEquals(accessTokens.size(), tc.limit);
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


        Exchange excCallResource = new Request.Builder().get(tc.getClientAddress() + "/malicious").buildExchange();
        LOG.debug("getting {}", excCallResource.getDestinations().getFirst());
        browser.apply(excCallResource); // will be aborted

        browser.clearCookies(); // send the auth link to some helpless (other) user

        excCallResource = browser.apply(new Request.Builder().get("http://localhost:1337" + ref.get()).buildExchange());

        assertEquals(400, excCallResource.getResponse().getStatusCode());

        assertTrue(excCallResource.getResponse().getBodyAsStringDecoded().contains("CSRF"));
    }

    @Test
    public void logout() throws Exception {
        browser.apply(new Request.Builder()
                .get(tc.getClientAddress() + "/init").buildExchange());

        var ili = browser.apply(new Request.Builder().get(tc.getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        browser.apply(new Request.Builder()
                .get(tc.getClientAddress() + "/logout").buildExchange());

        ili = browser.apply(new Request.Builder().get(tc.getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        assertEquals(0, browser.getCookieCount());
        assertTrue(didLogOut.get());
    }

    @Test
    public void staleSessionLogout() throws Exception {
        var ili = browser.apply(new Request.Builder().get(tc.getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        // call to /logout uses cookieHandlingHttpClient: *NOT* following the redirect (which would auto-login again)
        browser.applyWithoutRedirect(new Request.Builder()
                .get(tc.getClientAddress() + "/logout").buildExchange());

        ili = browser.apply(new Request.Builder().get(tc.getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        assertEquals(0, browser.getCookieCount());
    }

    @Test
    public void requestAuth() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/api/init").buildExchange();
        exc = browser.apply(exc);

        assertEquals(200, exc.getResponse().getStatusCode());

        b2cMembrane.requireAuth.setExpectedAud(UUID.randomUUID().toString());
        Exchange exc2 = new Request.Builder().get(tc.getClientAddress() + "/api/init").buildExchange();
        exc2 = browser.apply(exc2);

        assertTrue(exc2.getResponse().getStatusCode() >= 400);
    }

    @Test
    public void userFlowTest() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();
        exc = browser.apply(exc);

        String a1 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        assertNull(a1);

        exc = new Request.Builder().get(tc.getClientAddress() + "/api/").buildExchange();
        exc = browser.apply(exc);

        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        JwtClaims c2 = createJwtConsumer().processToClaims(a2);
        assertEquals(12, c2.getClaimsMap().size());
        assertEquals("b2c_1_susi", c2.getClaimValue("tfp"));
    }

    @Test
    public void userFlowViaInitiatorTest() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();
        browser.apply(exc);

        exc = new Request.Builder().get(tc.getClientAddress() + "/pe/init").buildExchange();
        exc = browser.apply(exc);

        String a1 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        assertNull(a1);

        exc = new Request.Builder().get(tc.getClientAddress() + "/api/").buildExchange();
        exc = browser.apply(exc);

        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        JwtClaims c2 = createJwtConsumer().processToClaims(a2);
        assertEquals(11, c2.getClaimsMap().size());
        assertEquals("b2c_1_profile_editing", c2.getClaimValue("tfp"));
    }

    @Test
    public void multipleUserFlowsTest() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();
        browser.apply(exc);

        exc = new Request.Builder().get(tc.getClientAddress() + "/pe2/init").buildExchange();
        browser.apply(exc);

        exc = new Request.Builder().get(tc.getClientAddress() + "/api/").buildExchange();
        exc = browser.apply(exc);

        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        JwtClaims c2 = createJwtConsumer().processToClaims(a2);
        assertEquals(11, c2.getClaimsMap().size());
        assertEquals("b2c_1_profile_editing2", c2.getClaimValue("tfp"));
    }

    @Test
    public void multipleUserFlowsWithErrorTest() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();
        browser.apply(exc);

        mockAuthorizationServer.returnOAuth2ErrorFromSignIn.set(true);

        exc = new Request.Builder().get(tc.getClientAddress() + "/pe2/init").buildExchange();
        browser.apply(exc);

        // here, an error is returned (as checked by {@link #errorDuringSignIn()}) and the user is logged out

        exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-auth-needed/").buildExchange();
        exc = browser.apply(exc);

        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        assertEquals("null", a2); // no access token, because user is logged out.
    }

    @Test
    public void stayLoggedInAfterProfileEditing2() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();
        browser.apply(exc);

        mockAuthorizationServer.abortSignIn.set(true);

        // /pe2/init keeps the user logged in while sending her to the AS
        exc = new Request.Builder().get(tc.getClientAddress() + "/pe2/init").buildExchange();
        exc = browser.apply(exc);

        assertEquals("signin aborted", exc.getResponse().getBodyAsStringDecoded());

        exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-auth-needed/").buildExchange();
        exc = browser.apply(exc);

        // valid access token, since still logged in
        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        JwtClaims c2 = createJwtConsumer().processToClaims(a2);
        assertEquals(11, c2.getClaimsMap().size());
        assertEquals("b2c_1_profile_editing2", c2.getClaimValue("tfp"));
    }

    @Test
    public void notLoggedInAfterProfileEditing() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init").buildExchange();
        browser.apply(exc);

        mockAuthorizationServer.abortSignIn.set(true);

        // /pe/init logs the user out before sending her to the AS
        exc = new Request.Builder().get(tc.getClientAddress() + "/pe/init").buildExchange();
        exc = browser.apply(exc);

        assertEquals("signin aborted", exc.getResponse().getBodyAsStringDecoded());

        exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-auth-needed/").buildExchange();
        exc = browser.apply(exc);

        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        assertEquals("null", a2); // no access token, because user is logged out.
    }


    @Test
    public void loginParams() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/init?login_hint=def&illegal=true").buildExchange();
        browser.applyWithoutRedirect(exc);

        String location = exc.getResponse().getHeader().getFirstValue("Location");

        URI jUri = new URIFactory().create(location);
        String q = jUri.getRawQuery();

        var params = URLParamUtil.parseQueryString(q, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

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
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/pe/init?domain_hint=flow&illegal=true").buildExchange();
        browser.applyWithoutRedirect(exc);

        String location = exc.getResponse().getHeader().getFirstValue("Location");

        URI jUri = new URIFactory().create(location);
        String q = jUri.getRawQuery();

        var params = URLParamUtil.parseQueryString(q, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

        assertTrue(params.containsKey("fooflow"));
        assertEquals("bar", params.get("foo"));
        assertTrue(params.containsKey("domain_hint"));
        assertEquals("flow", params.get("domain_hint"));
        assertFalse(params.containsKey("illegal"));
    }

    @Test
    public void loginNotRequired() throws Exception {
        // access 1: not authenticated, expecting no token
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-auth-needed/").buildExchange();
        exc = browser.applyWithoutRedirect(exc);

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        var res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertEquals("null", res.get("accessToken"));

        browser.apply(new Request.Builder().get(tc.getClientAddress() + "/pe/init").buildExchange());

        // access 2: authenticated, expecting JWT
        exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-auth-needed/").buildExchange();
        exc = browser.applyWithoutRedirect(exc);
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertTrue(((String)res.get("accessToken")).startsWith("eyJ"));
    }

    @Test
    public void returning4xx() throws Exception {
        // access 1: not authenticated, expecting 4xx
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-redirect/").buildExchange();
        exc = browser.applyWithoutRedirect(exc);

        assertEquals(403, exc.getResponse().getStatusCode());
        assertEquals("Forbidden", exc.getResponse().getStatusMessage());
        assertNull(exc.getResponse().getHeader().getFirstValue(SET_COOKIE));

        browser.apply(new Request.Builder().get(tc.getClientAddress() + "/pe/init").buildExchange());

        // access 2: authenticated, expecting JWT
        exc = new Request.Builder().get(tc.getClientAddress() + "/api-no-redirect/").buildExchange();
        exc = browser.applyWithoutRedirect(exc);
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        var res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertTrue(((String)res.get("accessToken")).startsWith("eyJ"));
    }

    @Test
    public void requireAuthRedirects() throws Exception {
        Exchange excCallResource = new Request.Builder().get(tc.getClientAddress() + "/api/").buildExchange();

        excCallResource = browser.applyWithoutRedirect(excCallResource);
        assertEquals(307, excCallResource.getResponse().getStatusCode());
        assertTrue(excCallResource.getResponse().getHeader().getFirstValue(Header.LOCATION).contains(
                ":"+mockAuthorizationServer.getServerPort()+"/"));
        assertFalse(didLogIn.get());
    }

    @Test
    public void errorDuringSignIn() throws Exception {
        mockAuthorizationServer.returnOAuth2ErrorFromSignIn.set(true);
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/api/").buildExchange();
        exc = browser.apply(exc);

        ProblemDetails pd = ProblemDetails.parse(exc.getResponse());
        assertEquals("https://membrane-api.io/problems/security", pd.getType());
        assertEquals("DEMO-123", pd.getInternal().get("error"));
    }

    @Test
    public void api1and2() throws Exception {
        Exchange exc = new Request.Builder().get(tc.getClientAddress() + "/api/").buildExchange();
        exc = browser.apply(exc);
        Map body = om.readValue(exc.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/api/", body.get("path"));
        assertEquals("", body.get("body"));
        assertEquals("GET", body.get("method"));
        assertTrue(((String)body.get("accessToken")).startsWith("eyJ"));

        exc = new Request.Builder().get(tc.getClientAddress() + "/api2/").buildExchange();
        exc = browser.apply(exc);
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
