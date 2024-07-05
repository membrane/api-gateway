/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.WellknownFile;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.MANUAL;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MockAuthorizationServer {
    public static final int SERVER_PORT = 1337;

    private final Runnable onLogin, onLogout;
    private final B2CTestConfig tc;
    private final SecureRandom rand = new SecureRandom();
    private final ObjectMapper om = new ObjectMapper();

    private HttpRouter mockAuthServer;

    private RsaJsonWebKey rsaJsonWebKey;
    private String jwksResponse;
    private String baseServerAddr;
    private String issuer;

    public final AtomicBoolean returnOAuth2ErrorFromSignIn = new AtomicBoolean();
    public final AtomicBoolean abortSignIn = new AtomicBoolean();
    public volatile int expiresIn;

    public MockAuthorizationServer(B2CTestConfig tc, Runnable onLogin, Runnable onLogout) {
        this.tc = tc;
        this.onLogin = onLogin;
        this.onLogout = onLogout;
    }

    public void init() throws IOException, JoseException {
        baseServerAddr = "http://localhost:" + getServerPort() + "/" + tc.tenantId.toString();
        issuer = baseServerAddr + "/v2.0/";
        createKey();

        mockAuthServer = new HttpRouter();
        mockAuthServer.getTransport().setBacklog(10000);
        mockAuthServer.getTransport().setSocketTimeout(10000);
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(tc.limit);
        mockAuthServer.getRuleManager().addProxy(getMockAuthServiceProxy(SERVER_PORT, tc.susiFlowId), MANUAL);
        mockAuthServer.getRuleManager().addProxy(getMockAuthServiceProxy(SERVER_PORT, tc.peFlowId), MANUAL);
        mockAuthServer.getRuleManager().addProxy(getMockAuthServiceProxy(SERVER_PORT, tc.pe2FlowId), MANUAL);
        mockAuthServer.start();
    }

    public void stop() {
        mockAuthServer.stop();
    }

    public Router getMockAuthServer() {
        return mockAuthServer;
    }

    public int getServerPort() {
        return SERVER_PORT;
    }

    private ServiceProxy getMockAuthServiceProxy(int port, String flowId) throws IOException {

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(port), null, 99999);

        Path path = new Path();
        path.setValue("/" + tc.tenantId + "/" + flowId + "/");
        sp.setPath(path);

        WellknownFile wkf = createWellKnown(flowId);

        sp.getInterceptors().add(new AbstractInterceptor() {

            final String baseUri = "/" + tc.tenantId + "/" + flowId;

            @Override
            public synchronized Outcome handleRequest(Exchange exc) throws Exception {
                Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                if (exc.getRequestURI().endsWith("/.well-known/openid-configuration")) {
                    exc.setResponse(Response.ok(wkf.getWellknown()).build());
                } else if (exc.getRequestURI().equalsIgnoreCase(baseUri + "/discovery/v2.0/keys")) {
                    String payload = "{ \"keys\":  [" + jwksResponse + "]}";
                    exc.setResponse(Response.ok(payload).contentType(APPLICATION_JSON).build());
                } else if (exc.getRequestURI().contains("/authorize?")) {
                    if (abortSignIn.get()) {
                        exc.setResponse(Response.internalServerError().body("signin aborted").build());
                        return Outcome.RETURN;
                    }
                    if (returnOAuth2ErrorFromSignIn.get()) {
                        exc.setResponse(Response.redirect(tc.getClientAddress() + "/oauth2callback?error=DEMO-123&error_description=This+is+a+demo+error.&state=" + params.get("state"), false).build());
                    } else {
                        onLogin.run();
                        exc.setResponse(Response.redirect(tc.getClientAddress() + "/oauth2callback?code=1234&state=" + params.get("state"), false).build());
                    }
                } else if (exc.getRequestURI().contains("/token")) {
                    handleTokenRequest(flowId, exc);
                } else if (exc.getRequestURI().contains("/logout")) {
                    onLogout.run();
                    exc.setResponse(Response.redirect( params.get("post_logout_redirect_uri"), false).build());
                }

                if (exc.getResponse() == null)
                    exc.setResponse(Response.notFound().build());
                return Outcome.RETURN;
            }
        });

        return sp;
    }

    private void handleTokenRequest(String flowId, Exchange exc) throws Exception {
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
        String secret = tc.clientId + ":" + tc.clientSecret;

        assertEquals("Basic " + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)) , exc.getRequest().getHeader().getFirstValue("Authorization"));

        exc.setResponse(Response.ok(om.writeValueAsString(
                createTokenResponse(flowId, params)
        )).contentType(APPLICATION_JSON).build());
    }

    private @NotNull Map<String, Object> createTokenResponse(String flowId, Map<String, String> params) throws JoseException, JsonProcessingException {
        Map<String, Object> res = new HashMap<>();

        String scope = params.get("scope");

        if (scope != null) {
            res.put("access_token", createToken(accessToken(flowId, scope.contains(tc.api1Id) ? tc.api1Id : tc.api2Id)));
            res.put("expires_in", expiresIn);
            var expires = NumericDate.now();
            expires.addSeconds(expiresIn);
            res.put("expires_on", expires.getValueInMillis());
            res.put("resource", tc.api1Id);
            res.put("scope", "https://localhost/" + tc.api1Id + "/Read");
        } else {
            res.put("scope", "offline_access openid");
        }
        res.put("token_type", "Bearer");

        var nbf = NumericDate.now();
        nbf.setValue(nbf.getValue() - 60);
        res.put("not_before", nbf);

        if (scope == null) {
            res.put("refresh_token", new BigInteger(130, rand).toString(32));
            res.put("refresh_token_expires_in", 1209600);

            res.put("id_token", createToken(idToken(flowId)));
            res.put("id_token_expires_in", expiresIn);
        }

        String profileInfo = om.writeValueAsString(createProfileInfo());

        res.put("profile_info", Base64.getUrlEncoder().encodeToString(profileInfo.getBytes(StandardCharsets.UTF_8)));
        return res;
    }

    private @NotNull HashMap<String, Object> createProfileInfo() {
        HashMap<String, Object> pi = new HashMap<>();
        pi.put("ver", "1.0");
        pi.put("tid", tc.tenantId);
        pi.put("sub", null);
        pi.put("name", tc.userFullName);
        pi.put("preferred_username", null);
        pi.put("idp", tc.idp);
        return pi;
    }

    private WellknownFile createWellKnown(String flowId) throws IOException {
        var wkf = new WellknownFile();

        String oaPrefix = "/" + flowId + "/oauth2/v2.0";

        wkf.setIssuer(issuer);
        wkf.setAuthorizationEndpoint(baseServerAddr + oaPrefix + "/authorize");
        wkf.setTokenEndpoint(baseServerAddr + oaPrefix + "/token");
        wkf.setEndSessionEndpoint(baseServerAddr + oaPrefix + "/logout");
        wkf.setJwksUri(baseServerAddr + "/" + flowId + "/discovery/v2.0/keys");

        wkf.setSupportedResponseTypes(ImmutableSet.of("code", "code id_token", "code token", "code id_token token", "id_token", "id_token token", "token", "token id_token"));
        wkf.setSupportedSubjectType("pairwise");
        wkf.setSupportedIdTokenSigningAlgValues("RS256");
        wkf.setSupportedScopes("openid");
        wkf.setSupportedTokenEndpointAuthMethods("client_secret_post");
        wkf.setSupportedClaims(ImmutableSet.of("name", "sub", "idp", "tfp", "iss", "iat", "exp", "aud", "acr", "nonce", "auth_time"));

        wkf.init(mockAuthServer);

        return wkf;
    }

    JwtClaims accessToken(String flowId, String aud) {
        JwtClaims jwtClaims = createBaseClaims();

        jwtClaims.setClaim("iss", issuer);
        jwtClaims.setClaim("idp", tc.idp);
        if (flowId.equals(tc.susiFlowId))
            jwtClaims.setClaim("name", tc.userFullName);
        jwtClaims.setClaim("sub", tc.sub);
        jwtClaims.setClaim("tfp", flowId);
        jwtClaims.setClaim("scp", "Read");
        jwtClaims.setClaim("azp", tc.clientId);
        jwtClaims.setClaim("ver", "1.0");
        jwtClaims.setClaim("aud", aud);
        return jwtClaims;
    }

    JwtClaims idToken(String flowId) {
        JwtClaims jwtClaims = createBaseClaims();

        jwtClaims.setClaim("iss", issuer);
        jwtClaims.setClaim("idp", tc.idp);
        jwtClaims.setClaim("name", tc.userFullName);
        jwtClaims.setClaim("sub", tc.sub);
        jwtClaims.setClaim("tfp", flowId);
        jwtClaims.setClaim("ver", "1.0");
        jwtClaims.setClaim("aud", tc.clientId);
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

    void createKey() throws JoseException {
        String serial = "1";
        RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId("k" + serial);
        rsaJsonWebKey.setAlgorithm("RS256");
        rsaJsonWebKey.setUse("sig");
        this.rsaJsonWebKey = rsaJsonWebKey;

        this.jwksResponse = rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }

    public Key getPublicKey() {
        return rsaJsonWebKey.getRsaPublicKey();
    }

    public void resetBehavior() {
        expiresIn = 60;
        returnOAuth2ErrorFromSignIn.set(false);
        abortSignIn.set(false);
    }

    public String getBaseServerAddr() {
        return baseServerAddr;
    }
}
