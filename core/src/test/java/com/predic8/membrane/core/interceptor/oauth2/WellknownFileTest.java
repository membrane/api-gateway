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

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.HttpRouter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WellknownFileTest {

    private static final String AUTH_SERVER_URL = "http://testserver.com/oauth2/";
    private static final String ISSUER = "http://testissuer.com";
    private static final String AUTH_ENDPOINT = AUTH_SERVER_URL + "auth";
    private static final String TOKEN_ENDPOINT = AUTH_SERVER_URL + "token";
    private static final String USERINFO_ENDPOINT = AUTH_SERVER_URL + "userinfo";
    private static final String REVOCATION_ENDPOINT = AUTH_SERVER_URL + "revoke";
    private static final String JWKS_URI = AUTH_SERVER_URL + "certs";

    private static WellknownFile wkf;

    @BeforeAll
    public static void setUp() throws Exception {
        wkf = new WellknownFile();
        wkf.setIssuer(ISSUER);
        wkf.setAuthorizationEndpoint(AUTH_ENDPOINT);
        wkf.setTokenEndpoint(TOKEN_ENDPOINT);
        wkf.setUserinfoEndpoint(USERINFO_ENDPOINT);
        wkf.setRevocationEndpoint(REVOCATION_ENDPOINT);
        wkf.setJwksUri(JWKS_URI);
        wkf.setSupportedResponseTypes("code token");
        wkf.setSupportedSubjectType("public");
        wkf.setSupportedIdTokenSigningAlgValues("RS256");
        wkf.setSupportedScopes("openid email profile");
        wkf.setSupportedTokenEndpointAuthMethods("client_secret_post");
        wkf.setSupportedClaims("sub email username");

        wkf.init(new HttpRouter());
    }

    @Test
    public void testIssuer() {
        assertEquals(ISSUER, wkf.getIssuer());
    }

    @Test
    public void testAuthorizationEndpoint() {
        assertEquals(AUTH_ENDPOINT, wkf.getAuthorizationEndpoint());
    }

    @Test
    public void testTokenEndpoint() {
        assertEquals(TOKEN_ENDPOINT, wkf.getTokenEndpoint());
    }

    @Test
    public void testUserinfoEndpoint() {
        assertEquals(USERINFO_ENDPOINT, wkf.getUserinfoEndpoint());
    }

    @Test
    public void testRevocationEndpoint() {
        assertEquals(REVOCATION_ENDPOINT, wkf.getRevocationEndpoint());
    }

    @Test
    public void testJwksUri() {
        assertEquals(JWKS_URI, wkf.getJwksUri());
    }

    @Test
    public void testSupportedResponseTypes() {
        assertEquals("code token", wkf.getSupportedResponseTypes());
    }

    @Test
    public void testSupportedSubjectTypes() {
        assertEquals("public", wkf.getSupportedSubjectType());
    }

    @Test
    public void testSupportedIdTokenSigningAlgValues() {
        assertEquals("RS256", wkf.getSupportedIdTokenSigningAlgValues());
    }

    @Test
    public void testSupportedScopes() {
        assertEquals("openid email profile", wkf.getSupportedScopes());
    }

    @Test
    public void testSupportedTokenEndpointAuthMethods() {
        assertEquals("client_secret_post", wkf.getSupportedTokenEndpointAuthMethods());
    }

    @Test
    public void testSupportedClaims() {
        assertEquals("sub email username", wkf.getSupportedClaims());
    }
}
