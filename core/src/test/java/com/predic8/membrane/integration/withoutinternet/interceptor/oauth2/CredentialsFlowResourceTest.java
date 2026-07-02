/*
 * Copyright 2026 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.integration.withoutinternet.interceptor.oauth2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.UserConfig;
import com.predic8.membrane.core.interceptor.oauth2.ClaimList;
import com.predic8.membrane.core.interceptor.oauth2.Client;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AuthorizationServerInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.StaticClientList;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.BearerJwtTokenGenerator;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.util.Util;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.annot.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests the RFC 8707 "resource" parameter and the per-client resource allowlist
 * in the client_credentials grant.
 */
public class CredentialsFlowResourceTest {

    private TestRouter router;
    private OAuth2AuthorizationServerInterceptor oasi;
    private BearerJwtTokenGenerator tokenGenerator;

    @BeforeEach
    void setUp() throws Exception {
        router = new TestRouter();
        router.start();
        oasi = new OAuth2AuthorizationServerInterceptor() {
            @Override
            public String computeBasePath() {
                return "";
            }
        };
        tokenGenerator = new BearerJwtTokenGenerator();
        tokenGenerator.setWarningGeneratedKey(false);
        oasi.setTokenGenerator(tokenGenerator);
        OAuth2AuthorizationServerInterceptor.RefreshTokenConfig refreshTokenConfig = new OAuth2AuthorizationServerInterceptor.RefreshTokenConfig();
        BearerJwtTokenGenerator refreshTokenGenerator = new BearerJwtTokenGenerator();
        refreshTokenGenerator.setWarningGeneratedKey(false);
        refreshTokenConfig.setTokenGenerator(refreshTokenGenerator);
        oasi.setRefreshTokenConfig(refreshTokenConfig);
        oasi.setIssueNonSpecRefreshTokens(true);
        oasi.setLocation("src/test/resources/oauth2/loginDialog/dialog");
        oasi.setConsentFile("src/test/resources/oauth2/consentFile.json");
        oasi.setPath("/login/");
        oasi.setIssuer("http://localhost:2001");
        setUserDataProvider();
        setClientList();
        setClaimList();
        oasi.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void noResourceParamAndNoAllowlistIssuesTokenWithoutAud() throws Exception {
        Exchange exc = tokenRequest("grant_type=client_credentials&client_id=unrestricted&client_secret=secret");
        assertEquals(200, exc.getResponse().getStatusCode());
        assertFalse(accessTokenClaims(exc).hasAudience());
    }

    @Test
    void accessTokenCarriesIssuer() throws Exception {
        Exchange exc = tokenRequest("grant_type=client_credentials&client_id=unrestricted&client_secret=secret");
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("http://localhost:2001", accessTokenClaims(exc).getIssuer());
    }

    @Test
    void noResourceParamIssuesTokenWithAllAllowedResourcesAsAud() throws Exception {
        Exchange exc = tokenRequest("grant_type=client_credentials&client_id=restricted&client_secret=secret");
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals(List.of("https://api.example.com", "https://billing.example.com"),
                accessTokenClaims(exc).getAudience());
    }

    @Test
    void requestedResourceBecomesAud() throws Exception {
        Exchange exc = tokenRequest("grant_type=client_credentials&client_id=restricted&client_secret=secret"
                                    + "&resource=https://api.example.com");
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals(List.of("https://api.example.com"), accessTokenClaims(exc).getAudience());
    }

    @Test
    void resourceNotInAllowlistIsRejected() throws Exception {
        assertInvalidTarget(tokenRequest("grant_type=client_credentials&client_id=restricted&client_secret=secret"
                                         + "&resource=https://evil.example.com"));
    }

    @Test
    void resourceForClientWithoutAllowlistIsRejected() throws Exception {
        assertInvalidTarget(tokenRequest("grant_type=client_credentials&client_id=unrestricted&client_secret=secret"
                                         + "&resource=https://api.example.com"));
    }

    @Test
    void nonAbsoluteResourceUriIsRejected() throws Exception {
        assertInvalidTarget(tokenRequest("grant_type=client_credentials&client_id=badlist&client_secret=secret"
                                         + "&resource=api"));
    }

    @Test
    void refreshedAccessTokenKeepsAud() throws Exception {
        Exchange exc = tokenRequest("grant_type=client_credentials&client_id=restricted&client_secret=secret"
                                    + "&resource=https://api.example.com");
        assertEquals(200, exc.getResponse().getStatusCode());
        String refreshToken = Util.parseSimpleJSONResponse(exc.getResponse()).get("refresh_token");

        Exchange refreshExc = tokenRequest("grant_type=refresh_token&refresh_token=" + refreshToken
                                           + "&client_id=restricted&client_secret=secret");
        assertEquals(200, refreshExc.getResponse().getStatusCode());
        assertEquals(List.of("https://api.example.com"), accessTokenClaims(refreshExc).getAudience());
    }

    private Exchange tokenRequest(String body) throws Exception {
        Exchange exc = new Request.Builder()
                .post("/oauth2/token")
                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .header(ACCEPT, APPLICATION_JSON)
                .header(USER_AGENT, USERAGENT)
                .body(body)
                .buildExchange();
        OAuth2TestUtil.makeExchangeValid(exc);
        oasi.handleRequest(exc);
        return exc;
    }

    private JwtClaims accessTokenClaims(Exchange exc) throws Exception {
        return tokenGenerator.verify(Util.parseSimpleJSONResponse(exc.getResponse()).get("access_token"));
    }

    private void assertInvalidTarget(Exchange exc) throws Exception {
        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals("invalid_target", Util.parseSimpleJSONResponse(exc.getResponse()).get("error"));
    }

    private void setClientList() {
        Client restricted = new Client("restricted", "secret", "http://localhost:2001/oauth2callback", "client_credentials,refresh_token");
        restricted.setResources("https://api.example.com https://billing.example.com");
        Client unrestricted = new Client("unrestricted", "secret", "http://localhost:2001/oauth2callback", "client_credentials");
        Client badlist = new Client("badlist", "secret", "http://localhost:2001/oauth2callback", "client_credentials");
        badlist.setResources("api");
        StaticClientList cl = new StaticClientList();
        cl.setClients(new ArrayList<>(List.of(restricted, unrestricted, badlist)));
        oasi.setClientList(cl);
    }

    private void setUserDataProvider() {
        StaticUserDataProvider udp = new StaticUserDataProvider();
        ArrayList<UserConfig> users = new ArrayList<>();
        users.add(new UserConfig("john", "password"));
        udp.setUsers(users);
        oasi.setUserDataProvider(udp);
    }

    private void setClaimList() {
        ClaimList cl = new ClaimList();
        cl.setValue("username email sub");
        ArrayList<ClaimList.Scope> scopes = new ArrayList<>();
        scopes.add(new ClaimList.Scope("profile", "username email"));
        cl.setScopes(scopes);
        oasi.setClaimList(cl);
    }
}
