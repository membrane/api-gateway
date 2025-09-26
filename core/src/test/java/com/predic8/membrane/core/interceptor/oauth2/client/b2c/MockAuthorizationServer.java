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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.jose4j.jwk.*;
import org.jose4j.jws.*;
import org.jose4j.jwt.*;
import org.jose4j.lang.*;

import java.io.*;
import java.math.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.oauth2.ParamNames.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class MockAuthorizationServer {
    public static final int SERVER_PORT = 21337;

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

    // refresh token -> corresponding flow
    public ConcurrentHashMap<String, String> refreshTokens = new ConcurrentHashMap<>();

    public MockAuthorizationServer(B2CTestConfig tc, Runnable onLogin, Runnable onLogout) {
        this.tc = tc;
        this.onLogin = onLogin;
        this.onLogout = onLogout;
    }

    public void init() throws IOException, JoseException {
        baseServerAddr = "http://localhost:" + getServerPort() + "/" + tc.tenantId;
        issuer = baseServerAddr + "/v2.0/";
        createKey();

        mockAuthServer = new HttpRouter();
        mockAuthServer.getTransport().setBacklog(10000);
        mockAuthServer.getTransport().setSocketTimeout(10000);
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getTransport().setConcurrentConnectionLimitPerIp(tc.limit * 100);
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
        path.setUri("/" + tc.tenantId + "/" + flowId + "/");
        sp.setPath(path);

        WellknownFile wkf = createWellKnown(flowId);

        sp.getFlow().add(new AbstractInterceptor() {

            final String baseUri = "/" + tc.tenantId + "/" + flowId;

            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    exc.setResponse(handleRequestInternal(exc));
                    return Outcome.RETURN;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public synchronized Response handleRequestInternal(Exchange exc) throws Exception {
                Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, ERROR);
                String requestURI = exc.getRequestURI();
                if (requestURI.endsWith("/.well-known/openid-configuration")) {
                    return Response.ok(wkf.getWellknown()).build();
                } else if (requestURI.equalsIgnoreCase(baseUri + "/discovery/v2.0/keys")) {
                    return Response
                            .ok("{ \"keys\":  [" + jwksResponse + "]}")
                            .contentType(APPLICATION_JSON)
                            .build();
                } else if (requestURI.contains("/authorize?")) {
                    if (abortSignIn.get()) {
                        return Response.internalServerError().body("signin aborted").build();
                    }
                    if (returnOAuth2ErrorFromSignIn.get()) {
                        return Response.redirect(tc.getClientAddress() + "/oauth2callback?error=DEMO-123&error_description=This+is+a+demo+error.&state=" + encode(params.get("state"), UTF_8), 302).build();
                    } else {
                        onLogin.run();
                        return Response.redirect(tc.getClientAddress() + "/oauth2callback?code=1234" + flowId + "&state=" + encode(params.get("state"), UTF_8), 302).build();
                    }
                } else if (requestURI.contains("/token")) {
                    return handleTokenRequest(flowId, exc);
                } else if (requestURI.contains("/logout")) {
                    onLogout.run();
                    return Response.redirect( params.get("post_logout_redirect_uri"), 302).build();
                } else {
                    return Response.notFound().build();
                }
            }
        });

        return sp;
    }

    private Response handleTokenRequest(String flowId, Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, ERROR);
        assertEquals(tc.clientId, params.get(CLIENT_ID));
        assertEquals(tc.clientSecret, params.get(CLIENT_SECRET));
        String grantType = params.get(GRANT_TYPE);
        if (grantType.equals("authorization_code")) {
            assertEquals("1234" + flowId, params.get(CODE));
            assertEquals("http://localhost:31337/oauth2callback", params.get(REDIRECT_URI));
        } else if (grantType.equals("refresh_token")) {
            String refreshToken = params.get(REFRESH_TOKEN);
            String flowId2 = refreshTokens.get(refreshToken);
            if (flowId2 == null)
                throw new RuntimeException("Refresh Token not known.");
            if (!flowId.equals(flowId2))
                throw new RuntimeException("Refresh Token valid for flow " + flowId2 + ", but used in flow " + flowId);
        } else {
            throw new RuntimeException("Illegal grant_type: " + grantType);
        }

        return Response
                .ok(om.writeValueAsString(createTokenResponse(flowId, params)))
                .contentType(APPLICATION_JSON)
                .build();
    }

    private @NotNull Map<String, Object> createTokenResponse(String flowId, Map<String, String> params) throws JoseException, JsonProcessingException {
        Map<String, Object> res = new HashMap<>();

        String scope = params.get(SCOPE);

        if (scope != null) {
            res.put("access_token", createToken(accessToken(flowId, scope.contains(tc.api1Id) ? tc.api1Id : tc.api2Id)));
            res.put("expires_in", expiresIn);
            res.put("expires_on", expiresInMillis(expiresIn));
            res.put("resource", tc.api1Id);
            res.put("scope", "https://localhost/" + tc.api1Id + "/Read");
        } else {
            String refreshToken = new BigInteger(130, rand).toString(32);
            refreshTokens.put(refreshToken, flowId);

            res.put("scope", "offline_access openid");
            res.put("refresh_token", refreshToken);
            res.put("refresh_token_expires_in", 1209600);
            res.put("id_token", createToken(idToken(flowId)));
            res.put("id_token_expires_in", expiresIn);
        }
        res.put("token_type", "Bearer");
        res.put("not_before", notBefore());
        res.put("profile_info", urlEncode(createProfileInfo()));
        return res;
    }

    private String urlEncode(Map<String, Object> map) throws JsonProcessingException {
        return Base64.getUrlEncoder().encodeToString(om.writeValueAsString(map).getBytes(UTF_8));
    }

    private static @NotNull NumericDate notBefore() {
        var nbf = NumericDate.now();
        nbf.setValue(nbf.getValue() - 60);
        return nbf;
    }

    private static long expiresInMillis(int seconds) {
        var expires = NumericDate.now();
        expires.addSeconds(seconds);
        return expires.getValueInMillis();
    }

    private @NotNull Map<String, Object> createProfileInfo() {
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

        wkf.init();

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
