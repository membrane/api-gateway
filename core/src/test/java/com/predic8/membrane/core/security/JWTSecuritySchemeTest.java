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
package com.predic8.membrane.core.security;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.jwt.Jwks;
import com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JWTSecuritySchemeTest {

    private static final String AUDIENCE = "AusgestelltFuer";

    public static final String SUB_CLAIM_CONTENT = "Till, der fleissige Programmierer";

    @Test
    public void test() throws Exception{
        RsaJsonWebKey privateKey = RsaJwkGenerator.generateJwk(2048);
        privateKey.setKeyId("membrane");

        RsaJsonWebKey publicOnly = new RsaJsonWebKey(privateKey.getRsaPublicKey());
        publicOnly.setKeyId("membrane");

        JwtAuthInterceptor interceptor = prepareInterceptor(publicOnly);

        Exchange exc = get("").header("Authorization", "Bearer " + getSignedJwt(privateKey) + "1").buildExchange();
        interceptor.handleRequest(exc);
    }

    private JwtAuthInterceptor prepareInterceptor(RsaJsonWebKey publicOnly) throws Exception {
        return initInterceptor(createInterceptor(publicOnly));
    }

    private JwtAuthInterceptor initInterceptor(JwtAuthInterceptor interceptor) {
        interceptor.init(new DummyTestRouter());
        return interceptor;
    }

    private JwtAuthInterceptor createInterceptor(RsaJsonWebKey publicOnly) {
        Jwks.Jwk jwk = new Jwks.Jwk();
        jwk.setContent(publicOnly.toJson());

        JwtAuthInterceptor interceptor = new JwtAuthInterceptor();
        Jwks jwks = new Jwks();
        jwks.setJwks(List.of(jwk));
        interceptor.setJwks(jwks);
        interceptor.setExpectedAud(AUDIENCE);
        return interceptor;
    }

    private static String getSignedJwt(RsaJsonWebKey privateKey) throws JoseException {
        return getSignedJwt(privateKey,createClaims(AUDIENCE));
    }

    private static JwtClaims createClaims(String audience){
        JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(10);
        claims.setIssuedAtToNow();
        claims.setNotBeforeMinutesInThePast(30);
        claims.setSubject(SUB_CLAIM_CONTENT);
        claims.setAudience(audience);
        claims.setClaim("scp", "read write admin");

        return claims;
    }

    private static String getSignedJwt(RsaJsonWebKey privateKey, JwtClaims claims) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(privateKey.getPrivateKey());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKeyIdHeaderValue("membrane");
        return jws.getCompactSerialization();
    }

    @Test
    void scopesFromString() {
        assertEquals(Set.of("read", "write"), new JWTSecurityScheme(Map.of("scp", "read write"), "scp").getScopes());
    }

    @Test
    void scopesFromList() {
        assertEquals(Set.of("read", "write"), new JWTSecurityScheme(Map.of("scp", List.of("read", "write")), "scp").getScopes());
    }

    @Test
    void scopesFromConfiguredScopeClaim() {
        assertEquals(Set.of("read", "write"), new JWTSecurityScheme(Map.of("scope", "read write"), "scope").getScopes());
    }

    @Test
    void onlyConfiguredClaimIsRead() {
        var jwt = Map.<String, Object>of("scp", "fromScp", "scope", "fromScope");
        assertEquals(Set.of("fromScp"), new JWTSecurityScheme(jwt, "scp").getScopes());
        assertEquals(Set.of("fromScope"), new JWTSecurityScheme(jwt, "scope").getScopes());
    }

    @Test
    void missingClaimMeansEmptyScopes() {
        assertEquals(Set.of(), new JWTSecurityScheme(Map.of("sub", "john"), "scp").getScopes());
    }
}
