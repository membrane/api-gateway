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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.WellknownFile;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.client.BrowserMock;
import com.predic8.membrane.core.interceptor.oauth2client.FlowInitiator;
import com.predic8.membrane.core.interceptor.oauth2client.LoginParameter;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.oauth2client.RequireAuth;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

public abstract class OAuth2ResourceB2CTest {

    private final int limit = 500;
    protected HttpRouter mockAuthServer;
    protected ObjectMapper om = new ObjectMapper();
    Logger LOG = LoggerFactory.getLogger(OAuth2ResourceB2CTest.class);
    int serverPort = 1337;
    UUID tenantId = UUID.randomUUID();
    BrowserMock browser = new BrowserMock();
    private final String serverHost = "localhost";
    private final int clientPort = 31337;
    private HttpRouter oauth2Resource;
    private OAuth2Resource2Interceptor oAuth2Resource2Interceptor;
    private RequireAuth requireAuth;
    private RsaJsonWebKey rsaJsonWebKey;
    private String jwksResponse;
    private final String api1Id = UUID.randomUUID().toString();
    private String baseServerAddr;
    private String issuer;
    private final String userFullName = "Mem Brane";
    private final String idp = "https://demo.predic8.de/api";
    private final String sub = UUID.randomUUID().toString();
    private final String clientId = UUID.randomUUID().toString();
    private final String clientSecret = "3423233123123";
    private final String susiFlowId = "b2c_1_susi";
    private final String peFlowId = "b2c_1_profile_editing";
    private volatile int expiresIn;
    private final AtomicBoolean didLogIn = new AtomicBoolean();

    private String getServerAddress() {
        return "http://" + serverHost + ":" + serverPort + "/" + tenantId.toString();
    }

    protected String getClientAddress() {
        return "http://" + serverHost + ":" + clientPort;
    }

    @BeforeEach
    public void init() throws Exception {
        expiresIn = 60;
        didLogIn.set(false);
        baseServerAddr = getServerAddress();
        issuer = baseServerAddr + "/v2.0/";

        createKey();

        mockAuthServer = new HttpRouter();
        mockAuthServer.getTransport().setBacklog(10000);
        mockAuthServer.getTransport().setSocketTimeout(10000);
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(limit);
        mockAuthServer.getRuleManager().addProxy(getMockAuthServiceProxy(serverPort, susiFlowId), RuleManager.RuleDefinitionSource.MANUAL);
        mockAuthServer.getRuleManager().addProxy(getMockAuthServiceProxy(serverPort, peFlowId), RuleManager.RuleDefinitionSource.MANUAL);
        mockAuthServer.start();

        oauth2Resource = new HttpRouter();
        oauth2Resource.getTransport().setBacklog(10000);
        oauth2Resource.getTransport().setSocketTimeout(10000);
        oauth2Resource.setHotDeploy(false);
        oauth2Resource.getTransport().setConcurrentConnectionLimitPerIp(limit);

        ServiceProxy sp1 = getConfiguredOAuth2Resource();
        ServiceProxy sp2 = getFlowInitiatorServiceProxy();
        sp1.init(oauth2Resource); // TODO backfired das sobald es keinen globalen oauth resource interceptor gibt?
        ServiceProxy sp3 = getRequireAuthServiceProxy();
        ServiceProxy sp4 = getRequireAuthNotRequiredServiceProxy();

        oauth2Resource.getRuleManager().addProxy(sp4, RuleManager.RuleDefinitionSource.MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp3, RuleManager.RuleDefinitionSource.MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp2, RuleManager.RuleDefinitionSource.MANUAL);
        oauth2Resource.getRuleManager().addProxy(sp1, RuleManager.RuleDefinitionSource.MANUAL);
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
        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init").buildExchange();

        excCallResource = browser.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));
        assertEquals("", body2.get("body"));
        assertEquals("GET", body2.get("method"));
        assertTrue(didLogIn.get());
    }

    @Test
    public void postOriginalRequest() throws Exception {
        Exchange excCallResource = new Request.Builder().post(getClientAddress() + "/init").body("demobody").buildExchange();

        excCallResource = browser.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class); // No
        assertEquals("/init", body2.get("path"));
        assertEquals("demobody", body2.get("body"));
        assertEquals("POST", body2.get("method"));
    }

    // this test also implicitly tests concurrency on oauth2resource
    @Test
    public void testUseRefreshTokenOnTokenExpiration() throws Exception {
        expiresIn = 1;

        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/init").buildExchange();

        excCallResource = browser.apply(excCallResource);
        Map body2 = om.readValue(excCallResource.getResponse().getBodyAsStream(), Map.class);
        assertEquals("/init", body2.get("path"));

        Set<String> accessTokens = new HashSet<>();
        List<Thread> threadList = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            threadList.add(new Thread(() -> {
                try {
                    cdl.countDown();
                    cdl.await();
                    String uuid = UUID.randomUUID().toString();
                    Exchange excCallResource2 = new Request.Builder().get(getClientAddress() + "/" + uuid).buildExchange();
                    excCallResource2 = browser.apply(excCallResource2);
                    Map body = om.readValue(excCallResource2.getResponse().getBodyAsStringDecoded(), Map.class);
                    String path = (String) body.get("path");
                    assertEquals("/" + uuid, path);
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
            assertEquals(accessTokens.size(), limit);
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
            public Outcome handleRequest(Exchange exc) throws Exception {
                if (state.get() == 0) {
                    ref.set(exc.getOriginalRequestUri());
                    state.set(1);
                    exc.setResponse(Response.internalServerError().body("").build());
                    return Outcome.RETURN;
                }

                return super.handleRequest(exc);
            }
        });


        Exchange excCallResource = new Request.Builder().get(getClientAddress() + "/malicious").buildExchange();
        LOG.debug("getting " + excCallResource.getDestinations().get(0));
        excCallResource = browser.apply(excCallResource); // will be aborted

        browser.clearCookies(); // send the auth link to some helpless (other) user

        excCallResource = browser.apply(new Request.Builder().get("http://localhost:1337" + ref.get()).buildExchange());

        assertEquals(400, excCallResource.getResponse().getStatusCode());

        assertTrue(excCallResource.getResponse().getBodyAsStringDecoded().contains("CSRF"));
    }

    @Test
    public void logout() throws Exception {
        browser.apply(new Request.Builder()
                .get(getClientAddress() + "/init").buildExchange());

        var ili = browser.apply(new Request.Builder().get(getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        // call to /logout uses cookieHandlingHttpClient: *NOT* following the redirect (which would auto-login again)
        browser.applyWithoutRedirect(new Request.Builder()
                .get(getClientAddress() + "/logout").buildExchange());

        ili = browser.apply(new Request.Builder().get(getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));

        assertEquals(0, browser.getCookieCount());
    }

    @Test
    public void requestAuth() throws Exception {
        Exchange exc = new Request.Builder().get(getClientAddress() + "/api/init").buildExchange();
        exc = browser.apply(exc);

        assertEquals(200, exc.getResponse().getStatusCode());

        requireAuth.setExpectedAud(UUID.randomUUID().toString());
        Exchange exc2 = new Request.Builder().get(getClientAddress() + "/api/init").buildExchange();
        exc2 = browser.apply(exc);

        assertTrue(exc2.getResponse().getStatusCode() >= 400);
    }

    @Test
    public void userFlowTest() throws Exception {
        JwtConsumer jc = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setVerificationKey(rsaJsonWebKey.getRsaPublicKey())
                .setExpectedAudience(true, api1Id).build();

        Exchange exc = new Request.Builder().get(getClientAddress() + "/init").buildExchange();
        exc = browser.apply(exc);

        String a1 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        JwtClaims c1 = jc.processToClaims(a1);
        assertEquals(c1.getClaimsMap().size(), 12);

        exc = new Request.Builder().get(getClientAddress() + "/pe/init").buildExchange();
        exc = browser.apply(exc);

        String a2 = (String) om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class).get("accessToken");
        JwtClaims c2 = jc.processToClaims(a2);
        assertEquals(c2.getClaimsMap().size(), 11);
        // weirdly the 'name' payload is missing here, see accessToken()
    }

    @Test
    public void loginParams() throws Exception {
        Exchange exc = new Request.Builder().get(getClientAddress() + "/init?login_hint=def&illegal=true").buildExchange();
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
        Exchange exc = new Request.Builder().get(getClientAddress() + "/pe/init?domain_hint=flow&illegal=true").buildExchange();
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
        Exchange exc = new Request.Builder().get(getClientAddress() + "/api2/").buildExchange();
        exc = browser.applyWithoutRedirect(exc);

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        Map res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertEquals("null", res.get("accessToken"));

        browser.apply(new Request.Builder().get(getClientAddress() + "/pe/init").buildExchange());

        // access 2: authenticated, expecting JWT
        exc = new Request.Builder().get(getClientAddress() + "/api2/").buildExchange();
        exc = browser.applyWithoutRedirect(exc);
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("Ok", exc.getResponse().getStatusMessage());
        res = om.readValue(exc.getResponse().getBodyAsStringDecoded(), Map.class);
        assertTrue(((String)res.get("accessToken")).startsWith("eyJ"));
    }


    void createKey() throws JoseException {
        String serial = "1";
        RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId("k" + serial);
        rsaJsonWebKey.setAlgorithm("RS256");
        rsaJsonWebKey.setUse("sig");
        this.rsaJsonWebKey = rsaJsonWebKey;

        this.jwksResponse = rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }

    JwtClaims accessToken(String flowId) {
        JwtClaims jwtClaims = createBaseClaims();

        jwtClaims.setClaim("iss", issuer);
        jwtClaims.setClaim("idp", idp);
        if (flowId.equals(susiFlowId))
            jwtClaims.setClaim("name", userFullName);
        jwtClaims.setClaim("sub", sub);
        jwtClaims.setClaim("tfp", flowId);
        jwtClaims.setClaim("scp", "Read");
        jwtClaims.setClaim("azp", clientId);
        jwtClaims.setClaim("ver", "1.0");
        jwtClaims.setClaim("aud", api1Id);
        return jwtClaims;
    }

    JwtClaims idToken(String flowId) {
        JwtClaims jwtClaims = createBaseClaims();

        jwtClaims.setClaim("iss", issuer);
        jwtClaims.setClaim("idp", idp);
        jwtClaims.setClaim("name", userFullName);
        jwtClaims.setClaim("sub", sub);
        jwtClaims.setClaim("tfp", flowId);
        jwtClaims.setClaim("ver", "1.0");
        jwtClaims.setClaim("aud", clientId);
        jwtClaims.setClaim("auth_time", jwtClaims.getClaimValue("iat"));
        return jwtClaims;
        /* not included in this test: "at_hash":"2VqmD1_Hz-y1MLUYF7AG_g" */
    }

    @NotNull
    private JwtClaims createBaseClaims() {
        JwtClaims jwtClaims = new JwtClaims();

        jwtClaims.setIssuedAtToNow();
        jwtClaims.setNotBeforeMinutesInThePast(3);

        NumericDate expiration = NumericDate.now();
        expiration.addSeconds(expiresIn);
        jwtClaims.setExpirationTime(expiration);
        return jwtClaims;
    }

    String createToken(JwtClaims claims) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(rsaJsonWebKey.getPrivateKey());
        jws.setKeyIdHeaderValue("k1");
        jws.setHeader("typ", "JWT");
        jws.setHeader("random", UUID.randomUUID().toString()); // this is not actually present, but required for the testUseRefreshTokenExpiration() to make tokens unique
        jws.setAlgorithmHeaderValue("RS256");

        return jws.getCompactSerialization();
    }

    private ServiceProxy getMockAuthServiceProxy(int port, String flowId) throws IOException {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(port), null, 99999);

        Path path = new Path();
        path.setValue("/" + tenantId + "/" + flowId);
        sp.setPath(path);

        WellknownFile wkf = new WellknownFile();

        String oaPrefix = "/" + flowId + "/oauth2/v2.0";

        wkf.setIssuer(issuer);
        wkf.setAuthorizationEndpoint(baseServerAddr + oaPrefix + "/authorize");
        wkf.setTokenEndpoint(baseServerAddr + oaPrefix + "/token");
        //wkf.setEndSessionEndpoint(baseServerAddr + oaPrefix + "/logout");
        wkf.setJwksUri(baseServerAddr + "/" + flowId + "/discovery/v2.0/keys");

        wkf.setSupportedResponseTypes(ImmutableSet.of("code", "code id_token", "code token", "code id_token token", "id_token", "id_token token", "token", "token id_token"));
        wkf.setSupportedSubjectType("pairwise");
        wkf.setSupportedIdTokenSigningAlgValues("RS256");
        wkf.setSupportedScopes("openid");
        wkf.setSupportedTokenEndpointAuthMethods("client_secret_post");
        wkf.setSupportedClaims(ImmutableSet.of("name", "sub", "idp", "tfp", "iss", "iat", "exp", "aud", "acr", "nonce", "auth_time"));

        wkf.init(new HttpRouter());

        sp.getInterceptors().add(new AbstractInterceptor() {

            final SecureRandom rand = new SecureRandom();

            final String baseUri = "/" + tenantId + "/" + flowId;

            @Override
            public synchronized Outcome handleRequest(Exchange exc) throws Exception {
                if (exc.getRequestURI().endsWith("/.well-known/openid-configuration")) {
                    exc.setResponse(Response.ok(wkf.getWellknown()).build());
                } else if (exc.getRequestURI().equalsIgnoreCase(baseUri + "/discovery/v2.0/keys")) {
                    String payload = "{ \"keys\":  [" + jwksResponse + "]}";
                    exc.setResponse(Response.ok(payload).contentType(APPLICATION_JSON).build());
                } else if (exc.getRequestURI().contains("/authorize?")) {
                    didLogIn.set(true);
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    exc.setResponse(Response.redirect(getClientAddress() + "/oauth2callback?code=1234&state=" + params.get("state"), false).build());
                } else if (exc.getRequestURI().contains("/token")) {
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    String grantType = params.get("grant_type");
                    if (grantType.equals("authorization_code")) {
                        assertEquals("1234", params.get("code"));
                        assertEquals("http://localhost:31337/oauth2callback", params.get("redirect_uri"));
                    } else if (grantType.equals("refresh_token")) {
                        String refreshToken = params.get("refresh_token");
                        assertTrue(refreshToken.length() > 10);
                        assertTrue(refreshToken.length() < 50);
                    } else {
                        throw new RuntimeException("Illegal grant_type: " + grantType);
                    }
                    String secret = clientId + ":" + clientSecret;

                    assertEquals("Basic " + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)) , exc.getRequest().getHeader().getFirstValue("Authorization"));

                    ObjectMapper om = new ObjectMapper();
                    Map<String, Object> res = new HashMap<>();

                    res.put("access_token", createToken(accessToken(flowId)));
                    res.put("token_type", "Bearer");
                    res.put("expires_in", expiresIn);

                    var expires = NumericDate.now();
                    expires.addSeconds(expiresIn);
                    res.put("expires_on", expires.getValueInMillis());

                    var nbf = NumericDate.now();
                    nbf.setValue(nbf.getValue() - 60);
                    res.put("not_before", nbf);

                    res.put("refresh_token", new BigInteger(130, rand).toString(32));
                    res.put("refresh_token_expires_in", 1209600);

                    res.put("id_token", createToken(idToken(flowId)));
                    res.put("id_token_expires_in", expiresIn);

                    HashMap<String, Object> pi = new HashMap<>();
                    pi.put("ver", "1.0");
                    pi.put("tid", tenantId);
                    pi.put("sub", null);
                    pi.put("name", userFullName);
                    pi.put("preferred_username", null);
                    pi.put("idp", idp);
                    String profileInfo = om.writeValueAsString(pi);

                    res.put("profile_info", Base64.getUrlEncoder().encodeToString(profileInfo.getBytes(StandardCharsets.UTF_8)));

                    res.put("resource", api1Id);
                    res.put("scope", "offline_access openid https://localhost/" + api1Id + "/Read");

                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType(APPLICATION_JSON).build());
                }

                if (exc.getResponse() == null)
                    exc.setResponse(Response.notFound().build());
                return Outcome.RETURN;
            }
        });

        return sp;
    }

    private ServiceProxy getRequireAuthServiceProxy() {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort), null, 99999);

        Path path = new Path();
        path.setValue("/api/");
        sp.setPath(path);

        var requireAuth = new RequireAuth();
        this.requireAuth = requireAuth;
        requireAuth.setExpectedAud(api1Id);
        requireAuth.setOauth2(oAuth2Resource2Interceptor);

        sp.getInterceptors().add(requireAuth);
        sp.getInterceptors().add(createTestResponseInterceptor());

        return sp;
    }

    private ServiceProxy getRequireAuthNotRequiredServiceProxy() {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort), null, 99999);

        Path path = new Path();
        path.setValue("/api2/");
        sp.setPath(path);

        var requireAuth = new RequireAuth();
        this.requireAuth = requireAuth;
        requireAuth.setExpectedAud(api1Id);
        requireAuth.setOauth2(oAuth2Resource2Interceptor);
        requireAuth.setRequired(false);

        sp.getInterceptors().add(requireAuth);
        sp.getInterceptors().add(createTestResponseInterceptor());

        return sp;
    }

    private ServiceProxy getFlowInitiatorServiceProxy() {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort), null, 99999);
        Path path = new Path();
        path.setValue("/pe/");
        sp.setPath(path);

        var flowInitiator = new FlowInitiator();

        flowInitiator.setDefaultFlow(susiFlowId);
        flowInitiator.setTriggerFlow(peFlowId);

        var lp1 = new LoginParameter();
        lp1.setName("domain_hint");

        var lp2 = new LoginParameter();
        lp2.setName("fooflow");
        lp2.setValue("bar");

        flowInitiator.setLoginParameters(List.of(
                lp1, lp2
        ));

        flowInitiator.setAfterLoginUrl("/");

        flowInitiator.setOauth2(oAuth2Resource2Interceptor);

        sp.getInterceptors().add(flowInitiator);
        sp.getInterceptors().add(createTestResponseInterceptor());

        return sp;
    }

    private ServiceProxy getConfiguredOAuth2Resource() {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(clientPort), null, 99999);

        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = new OAuth2Resource2Interceptor();
        this.oAuth2Resource2Interceptor = oAuth2ResourceInterceptor;
        configureSessionManager(oAuth2ResourceInterceptor);

        MembraneAuthorizationService auth = new MembraneAuthorizationService();
        auth.setSrc(baseServerAddr + "/" + susiFlowId + "/v2.0");
        auth.setClientId(clientId);
        auth.setClientSecret(clientSecret);
        auth.setScope("openid profile offline_access https://localhost/" + api1Id + "/Read");
        auth.setSubject("sub");

        oAuth2ResourceInterceptor.setAuthService(auth);

        oAuth2ResourceInterceptor.setLogoutUrl("/logout");
        oAuth2ResourceInterceptor.setSkipUserInfo(true);
        oAuth2ResourceInterceptor.setAppendAccessTokenToRequest(true);

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


        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                if (!exc.getRequest().getUri().contains("is-logged-in"))
                    return Outcome.CONTINUE;

                boolean isLoggedIn = oAuth2ResourceInterceptor.getSessionManager().getSession(exc).isVerified();

                exc.setResponse(Response.ok("{\"success\":" + isLoggedIn + "}").header(Header.CONTENT_TYPE, APPLICATION_JSON).build());
                return Outcome.RETURN;
            }
        });
        sp.getInterceptors().add(oAuth2ResourceInterceptor);
        sp.getInterceptors().add(createTestResponseInterceptor());
        return sp;
    }

    @NotNull
    private AbstractInterceptor createTestResponseInterceptor() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                OAuth2AnswerParameters answer = OAuth2AnswerParameters.deserialize(String.valueOf(exc.getProperty(Exchange.OAUTH2)));
                String accessToken = answer == null ? "null" : answer.getAccessToken();
                Map<String, String> body = Map.of(
                        "accessToken", accessToken,
                        "path", exc.getRequestURI(),
                        "method", exc.getRequest().getMethod(),
                        "body", exc.getRequest().getBodyAsStringDecoded()
                );

                exc.setResponse(Response.ok(om.writeValueAsString(body)).build());
                return Outcome.RETURN;
            }
        };
    }

    protected abstract void configureSessionManager(OAuth2Resource2Interceptor oauth2);
}
