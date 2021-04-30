/*
 * Copyright 2021 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.jwt;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class JwtAuthInterceptorTest{

    public static final String KID = "membrane";
    public static final String SUB_CLAIM_CONTENT = "Till, der fleiﬂige Programmierer";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
                happyPath()
        });
    }

    private static Object[] happyPath() {
        return new Object[] {
                "happyPath",
                (FunctionWithException<RsaJsonWebKey,Exchange>)(RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + getSignedJwt(privateKey))
                        .buildExchange(),
                (Consumer<Exchange>)(Exchange exc) -> {
                    assertNotNull(exc.getProperties().get("jwt"));
                    assertEquals(SUB_CLAIM_CONTENT, ((Map)exc.getProperties().get("jwt")).get("sub"));
                }
        };
    }

    public static interface FunctionWithException<T,R>{
        R call(T param) throws Exception;
    }

    @Parameterized.Parameter
    public String testName;
    @Parameterized.Parameter(value=1)
    public FunctionWithException<RsaJsonWebKey,Exchange> exchangeCreator;
    @Parameterized.Parameter(value = 2)
    public Consumer<Exchange> asserts;

    @Test
    public void test() throws Exception{
        RsaJsonWebKey privateKey = RsaJwkGenerator.generateJwk(2048);

        RsaJsonWebKey publicOnly = new RsaJsonWebKey(privateKey.getRsaPublicKey());
        publicOnly.setKeyId(KID);

        JwtAuthInterceptor interceptor = prepareInterceptor(publicOnly);

        Exchange exc = exchangeCreator.call(privateKey);

        interceptor.handleRequest(exc);

        asserts.call(exc);
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
        return interceptor;
    }

    private static String getSignedJwt(RsaJsonWebKey privateKey) throws JoseException {
        JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(10);
        claims.setIssuedAtToNow();
        claims.setNotBeforeMinutesInThePast(30);
        claims.setSubject(SUB_CLAIM_CONTENT);

        JsonWebSignature jws = new JsonWebSignature();

        jws.setPayload(claims.toJson());

        jws.setKey(privateKey.getPrivateKey());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKeyIdHeaderValue(KID);

        return jws.getCompactSerialization();
    }

}