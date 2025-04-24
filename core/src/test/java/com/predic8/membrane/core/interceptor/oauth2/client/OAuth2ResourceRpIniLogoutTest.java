/*
 * Copyright 2024 predic8 GmbH, www.predic8.com
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.WellknownFile;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.MembraneAuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.LoginParameter;
import com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor;
import com.predic8.membrane.core.interceptor.oauth2client.rf.FormPostGenerator;
import com.predic8.membrane.core.interceptor.session.InMemorySessionManager;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.*;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2ResourceRpIniLogoutTest {

    protected final BrowserMock browser = new BrowserMock();
    protected HttpRouter mockAuthServer;
    protected final ObjectMapper om = new ObjectMapper();
    final int serverPort = 3062;
    private final String serverHost = "localhost";
    private final int clientPort = 31337;
    private HttpRouter oauth2Resource;
    private final AtomicBoolean isLoggedOutAtOP = new AtomicBoolean(false);
    private RsaJsonWebKey rsaJsonWebKey;
    private String jwksResponse;

    private String getServerAddress() {
        return "http://" + serverHost + ":" + serverPort;
    }

    protected String getClientAddress() {
        return "http://" + serverHost + ":" + clientPort;
    }

    @BeforeEach
    public void init() throws IOException, JoseException {
        createKey();

        mockAuthServer = new HttpRouter();
        mockAuthServer.setHotDeploy(false);
        mockAuthServer.getRuleManager().addProxyAndOpenPortIfNew(getMockAuthServiceProxy());
        mockAuthServer.start();

        oauth2Resource = new HttpRouter();
        oauth2Resource.setHotDeploy(false);
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
    public void logout() throws Exception {
        isLoggedOutAtOP.set(false);

        browser.apply(new Request.Builder()
                .get(getClientAddress() + "/init").buildExchange());

        var ili = browser.apply(new Request.Builder().get(getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("true"));

        var ilo = browser.apply(new Request.Builder().get(getClientAddress() + "/logout").buildExchange());
        assertTrue(ilo.getResponse().getBodyAsStringDecoded().contains("logged out!"));

        ili = browser.apply(new Request.Builder().get(getClientAddress() + "/is-logged-in").buildExchange());

        assertTrue(ili.getResponse().getBodyAsStringDecoded().contains("false"));
        assertTrue(isLoggedOutAtOP.get());
        assertEquals("/after-logout", ilo.getRequest().getUri());
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

    @NotNull
    private JwtClaims createBaseClaims() {
        JwtClaims jwtClaims = new JwtClaims();

        jwtClaims.setIssuedAtToNow();
        jwtClaims.setNotBeforeMinutesInThePast(3);

        NumericDate expiration = NumericDate.now();
        expiration.addSeconds(60);
        jwtClaims.setExpirationTime(expiration);
        return jwtClaims;
    }

    JwtClaims idToken() {
        JwtClaims jwtClaims = createBaseClaims();

        jwtClaims.setClaim("iss", getServerAddress());
        jwtClaims.setClaim("sub", "john");
        jwtClaims.setClaim("ver", "1.0");
        jwtClaims.setClaim("aud", "2343243242");
        jwtClaims.setClaim("auth_time", jwtClaims.getClaimValue("iat"));
        return jwtClaims;
        /* not included in this test: "at_hash":"2VqmD1_Hz-y1MLUYF7AG_g" */
    }

    private ServiceProxy getMockAuthServiceProxy() throws IOException {
        JwtConsumer jc = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setVerificationKey(rsaJsonWebKey.getRsaPublicKey())
                .setExpectedAudience(true, "2343243242").build();

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(serverPort), null, 99999);


        WellknownFile wkf = getWellknownFile();
        wkf.init(new HttpRouter());

        sp.getInterceptors().add(new AbstractInterceptor() {

            final SecureRandom rand = new SecureRandom();

            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    return handleRequestInternal(exc);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public synchronized Outcome handleRequestInternal(Exchange exc) throws URISyntaxException, IOException, JoseException, InvalidJwtException {
                if (exc.getRequestURI().endsWith("/.well-known/openid-configuration")) {
                    exc.setResponse(Response.ok(wkf.getWellknown()).build());
                } else if (exc.getRequestURI().equals("/certs")) {
                    String payload = "{ \"keys\":  [" + jwksResponse + "]}";
                    exc.setResponse(Response.ok(payload).contentType(APPLICATION_JSON).build());
                } else if (exc.getRequestURI().startsWith("/auth?")) {
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    exc.setResponse(new FormPostGenerator(getClientAddress() + "/oauth2callback")
                            .withParameter("state", params.get("state"))
                            .withParameter("code", params.get("1234"))
                            .build());
                } else if (exc.getRequestURI().startsWith("/token")) {
                    ObjectMapper om = new ObjectMapper();
                    Map<String, String> res = new HashMap<>();
                    res.put("access_token", new BigInteger(130, rand).toString(32));
                    res.put("token_type", "bearer");
                    res.put("expires_in", "1");
                    res.put("refresh_token", new BigInteger(130, rand).toString(32));
                    res.put("id_token", createToken(idToken()));
                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType(APPLICATION_JSON).build());

                } else if (exc.getRequestURI().startsWith("/userinfo")) {
                    ObjectMapper om = new ObjectMapper();
                    Map<String, String> res = new HashMap<>();
                    res.put("username", "dummy");
                    exc.setResponse(Response.ok(om.writeValueAsString(res)).contentType(APPLICATION_JSON).build());
                } else if (exc.getRequestURI().startsWith("/end-session")) {
                    Map<String, String> params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
                    assertNotNull(params.get("id_token_hint"));
                    JwtClaims claims = jc.processToClaims(params.get("id_token_hint"));
                    assertEquals(8, claims.getClaimsMap().size());
                    String uri = params.get("post_logout_redirect_uri");
                    assertNotNull(uri);
                    exc.setResponse(Response.redirect(uri, 303).build());
                    isLoggedOutAtOP.set(true);
                }

                if (exc.getResponse() == null)
                    exc.setResponse(Response.notFound().build());
                return RETURN;
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
        wkf.setEndSessionEndpoint(getServerAddress() + "/end-session");
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
                if (exc.getRequest().getUri().equals("/after-logout")) {
                    exc.setResponse(Response.ok("logged out!").build());
                    return RETURN;
                }
                return Outcome.CONTINUE;
            }
        });
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (!exc.getRequest().getUri().contains("is-logged-in"))
                    return Outcome.CONTINUE;

                boolean isLoggedIn = oAuth2ResourceInterceptor.getSessionManager().getSession(exc).isVerified();

                exc.setResponse(Response.ok("{\"success\":" + isLoggedIn + "}").header(Header.CONTENT_TYPE, APPLICATION_JSON).build());
                return RETURN;
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
                return RETURN;
            }
        });
        return sp;
    }

    private @NotNull OAuth2Resource2Interceptor getoAuth2Resource2Interceptor() {
        OAuth2Resource2Interceptor oAuth2ResourceInterceptor = new OAuth2Resource2Interceptor();
        oAuth2ResourceInterceptor.setSessionManager(new InMemorySessionManager());
        MembraneAuthorizationService auth = new MembraneAuthorizationService();
        auth.setSrc(getServerAddress());
        auth.setClientId("2343243242");
        auth.setClientSecret("3423233123123");
        auth.setScope("openid profile");
        oAuth2ResourceInterceptor.setAuthService(auth);

        oAuth2ResourceInterceptor.setLogoutUrl("/logout");
        oAuth2ResourceInterceptor.setAfterLogoutUrl("/after-logout");

        var withOutValue = new LoginParameter();
        withOutValue.setName("login_hint");

        var withValue = new LoginParameter();
        withValue.setName("foo");
        withValue.setValue("bar");

        oAuth2ResourceInterceptor.setLoginParameters(List.of(
                withOutValue,
                withValue
        ));
        return oAuth2ResourceInterceptor;
    }
}
