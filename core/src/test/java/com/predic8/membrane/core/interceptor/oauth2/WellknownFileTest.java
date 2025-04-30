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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

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

        wkf.init();
    }

    @Test
    public void testWellKnownSerialization() {
        assertEquals("{\"issuer\":\"http://testissuer.com\",\"authorization_endpoint\":\"http://testserver.com/oauth2/auth\"" +
                ",\"token_endpoint\":\"http://testserver.com/oauth2/token\",\"userinfo_endpoint\":\"http://testserver.com/oauth2/userinfo\"," +
                "\"revocation_endpoint\":\"http://testserver.com/oauth2/revoke\",\"jwks_uri\":\"http://testserver.com/oauth2/certs\"," +
                "\"response_types_supported\":[\"code\",\"token\"],\"subject_types_supported\":[\"public\"]," +
                "\"id_token_signing_alg_values_supported\":[\"RS256\"],\"scopes_supported\":[\"openid\",\"email\",\"profile\"]," +
                "\"token_endpoint_auth_methods_supported\":[\"client_secret_post\"],\"claims_supported\":[\"sub\",\"email\",\"username\"]}",
                wkf.getWellknown());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testJSONSerializationParts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> jsonMap = mapper.readValue(wkf.getWellknown(), new TypeReference<>() {
        });

        assertEquals(ISSUER, jsonMap.get("issuer"));
        assertEquals(AUTH_ENDPOINT, jsonMap.get("authorization_endpoint"));
        assertEquals(TOKEN_ENDPOINT, jsonMap.get("token_endpoint"));
        assertEquals(USERINFO_ENDPOINT, jsonMap.get("userinfo_endpoint"));
        assertEquals(REVOCATION_ENDPOINT, jsonMap.get("revocation_endpoint"));
        assertEquals(JWKS_URI, jsonMap.get("jwks_uri"));
        assertEquals(Set.of("code", "token"), new HashSet<>((List<String>) jsonMap.get("response_types_supported")));
        assertEquals(Set.of("public"), new HashSet<>((List<String>)jsonMap.get("subject_types_supported")));
        assertEquals(Set.of("RS256"), new HashSet<>((List<String>)jsonMap.get("id_token_signing_alg_values_supported")));
        assertEquals(Set.of("openid", "email", "profile"), new HashSet<>((List<String>)jsonMap.get("scopes_supported")));
        assertEquals(Set.of("client_secret_post"), new HashSet<>((List<String>)jsonMap.get("token_endpoint_auth_methods_supported")));
        assertEquals(Set.of("sub", "email", "username"), new HashSet<>((List<String>)jsonMap.get("claims_supported")));
    }
}
