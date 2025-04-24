/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.jwt.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.security.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.jose4j.jwk.*;
import org.jose4j.jws.*;
import org.jose4j.jwt.*;
import org.jose4j.lang.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.openapi.util.TestUtils.*;
import static com.predic8.membrane.test.TestUtil.getPathFromResource;
import static org.junit.jupiter.api.Assertions.*;

public class JWTInterceptorAndSecurityValidatorTest {

    public static final String SPEC_LOCATION = getPathFromResource( "openapi/openapi-proxy/no-extensions.yml");
    APIProxy proxy;

    RsaJsonWebKey privateKey;


    @BeforeEach
    public void setUp() throws Exception {

        Router router = new Router();
        proxy = createProxy(router, getSpec());

        privateKey = RsaJwkGenerator.generateJwk(2048);
        privateKey.setKeyId("membrane");

        proxy.getInterceptors().add(getJwtAuthInterceptor(router));

        router.setTransport(new HttpTransport());
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.init();
    }

    @Test
    public void checkIfScopesAreStoredInProperty() throws Exception {
        Exchange exc = new Request.Builder().get("/foo").header("Authorization", "bearer " + getSignedJwt(privateKey, getJwtClaimsStringScopesList())).buildExchange();
        callInterceptorChain(exc);

        //noinspection unchecked
        assertEquals(new HashSet<>(List.of("write","admin","read")), ((List<SecurityScheme>)exc.getProperty(SECURITY_SCHEMES)).getFirst().getScopes());
    }

    @Test
    public void checkIfScopesCanBeReadFromListType() throws Exception {
        Exchange exc = new Request.Builder().get("/foo").header("Authorization", "bearer " + getSignedJwt(privateKey, getJwtClaimsArrayScopesList())).buildExchange();
        callInterceptorChain(exc);

        //noinspection unchecked
        assertEquals(new HashSet<>(List.of("write","admin","read")), ((List<SecurityScheme>)exc.getProperty(SECURITY_SCHEMES)).getFirst().getScopes());
    }

    private void callInterceptorChain(Exchange exc) {
        proxy.getInterceptors().forEach(interceptor -> {
            try {
                interceptor.handleRequest(exc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @NotNull
    private JwtAuthInterceptor getJwtAuthInterceptor(Router router) {
        JwtAuthInterceptor interceptor = createInterceptor(getPublicKey());
        interceptor.setJwtRetriever(new HeaderJwtRetriever("Authorization","bearer"));
        interceptor.init(router);
        return interceptor;
    }

    @NotNull
    private RsaJsonWebKey getPublicKey() {
        RsaJsonWebKey publicOnly = new RsaJsonWebKey(privateKey.getRsaPublicKey());
        publicOnly.setKeyId("membrane");
        return publicOnly;
    }

    @NotNull
    private static OpenAPISpec getSpec() {
        OpenAPISpec spec = new OpenAPISpec();
        spec.location = SPEC_LOCATION;
        return spec;
    }

    private JwtAuthInterceptor createInterceptor(RsaJsonWebKey publicOnly) {
        JwtAuthInterceptor interceptor = new JwtAuthInterceptor();
        interceptor.setJwks(getJwks(publicOnly));
        interceptor.setExpectedAud("11235");
        return interceptor;
    }

    @NotNull
    private static Jwks getJwks(RsaJsonWebKey publicOnly) {
        Jwks.Jwk jwk = new Jwks.Jwk();
        jwk.setContent(publicOnly.toJson());
        Jwks jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        jwks.getJwks().add(jwk);
        return jwks;
    }

    @NotNull
    private static JwtClaims getJwtClaimsStringScopesList() {
        JwtClaims claims = new JwtClaims();
        claims.setSubject("Alice");
        claims.setClaim("scp", "read write admin");
        claims.setAudience("11235");
        claims.setExpirationTime(expireIn5Minutes());
        return claims;
    }

    @NotNull
    private static JwtClaims getJwtClaimsArrayScopesList() {
        JwtClaims claims = new JwtClaims();
        claims.setSubject("Alice");
        claims.setClaim("scp", List.of("read", "write", "admin"));
        claims.setAudience("11235");
        claims.setExpirationTime(expireIn5Minutes());
        return claims;
    }

    @NotNull
    private static NumericDate expireIn5Minutes() {
        NumericDate exp = NumericDate.now();
        exp.addSeconds(300);
        return exp;
    }

    private static String getSignedJwt(RsaJsonWebKey privateKey, JwtClaims claims) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(privateKey.getPrivateKey());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKeyIdHeaderValue(privateKey.getKeyId());
        return jws.getCompactSerialization();
    }
}
