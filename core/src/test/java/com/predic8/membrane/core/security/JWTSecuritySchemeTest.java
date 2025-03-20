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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.jwt.*;
import com.predic8.membrane.core.resolver.*;
import org.jose4j.jwk.*;
import org.jose4j.jws.*;
import org.jose4j.jwt.*;
import org.jose4j.lang.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        Exchange exc = new Request.Builder()
                .get("")
                .header("Authorization", "Bearer " + getSignedJwt(privateKey) + "1")
                .buildExchange();

        interceptor.handleRequest(exc);

        //
    }

    private JwtAuthInterceptor prepareInterceptor(RsaJsonWebKey publicOnly) throws Exception {
        return initInterceptor(createInterceptor(publicOnly));
    }

    private JwtAuthInterceptor initInterceptor(JwtAuthInterceptor interceptor) throws Exception {
        Router routerMock = mock(Router.class);
        when(routerMock.getBaseLocation()).thenReturn("");
        when(routerMock.getResolverMap()).thenReturn(new ResolverMap());
        interceptor.init(routerMock);
        return interceptor;
    }

    private JwtAuthInterceptor createInterceptor(RsaJsonWebKey publicOnly) {
        Jwks.Jwk jwk = new Jwks.Jwk();
        jwk.setContent(publicOnly.toJson());

        JwtAuthInterceptor interceptor = new JwtAuthInterceptor();
        Jwks jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        jwks.getJwks().add(jwk);
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

}
