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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JwtAuthInterceptorTest{

    public static final String KID = "membrane";
    public static final String SUB_CLAIM_CONTENT = "Till, der fleissige Programmierer";
    private static final String AUDIENCE = "AusgestelltFuer";

    public static Stream<Named<TestData>> data() throws Exception {
        return Stream.of(happyPath(),
                wrongAudience(),
                manipulatedSignature(),
                unknownKey(),
                wrongKId(),
                unknownKeyWithCorrectKid(),
                noJwtInHeader(),
                malformedJwt(),
                threeDotsNonJwt()
        ).map(data -> Named.of(data.testName(), data));
    }

    private static TestData threeDotsNonJwt() {
        return new TestData(
                "threeDotsNonJwt",
                (RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + "header.body.sig")
                        .buildExchange(),
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_DECODED_HEADER_NOT_JSON, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData malformedJwt() {
        return new TestData(
                "malformedJwt",
                (RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + "malformed.jwt")
                        .buildExchange(),
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JsonWebToken.ERROR_MALFORMED_COMPACT_SERIALIZATION, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData noJwtInHeader() {
        return new TestData(
                "noJwtInHeader",
                (RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .buildExchange(),
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_JWT_NOT_FOUND, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData unknownKeyWithCorrectKid() {
        return new TestData(
                "unknownKeyWithCorrectKid",
                (RsaJsonWebKey privateKey) -> {
                    RsaJsonWebKey privateKey1 = RsaJwkGenerator.generateJwk(2048);
                    privateKey1.setKeyId(KID);
                    return new Request.Builder()
                            .get("")
                            .header("Authorization", "Bearer " + getSignedJwt(privateKey1) + "1")
                            .buildExchange();
                },
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_VALIDATION_FAILED, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData wrongKId() {
        return new TestData(
                "wrongKId",
                (RsaJsonWebKey privateKey) -> {
                    privateKey.setKeyId(KID + "q2342341");
                    return new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + getSignedJwt(privateKey) + "1")
                        .buildExchange();
                },
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_UNKNOWN_KEY, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData unknownKey() {
        return new TestData(
                "unknownKey",
                (RsaJsonWebKey privateKey) -> {
                    RsaJsonWebKey privateKey1 = RsaJwkGenerator.generateJwk(2048);
                    privateKey1.setKeyId(KID + "q2342341");
                    return new Request.Builder()
                            .get("")
                            .header("Authorization", "Bearer " + getSignedJwt(privateKey1) + "1")
                            .buildExchange();
                },
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_UNKNOWN_KEY, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData manipulatedSignature() {
        return new TestData(
                "manipulatedSignature",
                (RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + getSignedJwt(privateKey) + "1")
                        .buildExchange(),
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_VALIDATION_FAILED, unpackBody(exc).get("description"));
                }
        );
    }

    private static TestData wrongAudience() {
        return new TestData(
                "wrongAudience",
                (RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + getSignedJwt(privateKey, getClaimsWithWrongAudience()))
                        .buildExchange(),
                (Exchange exc) -> {
                    assertTrue(exc.getResponse().isUserError());
                    assertNull(exc.getProperties().get("jwt"));
                    assertEquals(JwtAuthInterceptor.ERROR_VALIDATION_FAILED, unpackBody(exc).get("description"));
                }
        );
    }


    private static TestData happyPath() {
        return new TestData(
                "happyPath",
                (RsaJsonWebKey privateKey) -> new Request.Builder()
                        .get("")
                        .header("Authorization", "Bearer " + getSignedJwt(privateKey))
                        .buildExchange(),
                (Exchange exc) -> {
                    assertNotNull(exc.getProperties().get("jwt"));
                    assertEquals(SUB_CLAIM_CONTENT, ((Map<?, ?>)exc.getProperties().get("jwt")).get("sub"));
                }
        );
    }

    private static Map<String,Object> unpackBody(Exchange exc) {
        try {
            return new ObjectMapper().readValue(exc.getResponse().getBodyAsStream(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public interface FunctionWithException<T,R>{

        R call(T param) throws Exception;
    }

    private record TestData(
            String testName,
            FunctionWithException<RsaJsonWebKey,Exchange> exchangeCreator,
            ExceptionThrowingConsumer<Exchange> asserts
    ) {}

    @ParameterizedTest
    @MethodSource("data")
    public void test(TestData data) throws Exception{
        RsaJsonWebKey privateKey = RsaJwkGenerator.generateJwk(2048);
        privateKey.setKeyId(KID);

        RsaJsonWebKey publicOnly = new RsaJsonWebKey(privateKey.getRsaPublicKey());
        publicOnly.setKeyId(KID);

        JwtAuthInterceptor interceptor = prepareInterceptor(publicOnly);

        Exchange exc = data.exchangeCreator().call(privateKey);

        interceptor.handleRequest(exc);

        data.asserts().accept(exc);
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

    private static String getSignedJwt(RsaJsonWebKey privateKey, JwtClaims claims) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();

        jws.setPayload(claims.toJson());

        jws.setKey(privateKey.getPrivateKey());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKeyIdHeaderValue(privateKey.getKeyId());

        return jws.getCompactSerialization();
    }

    private static JwtClaims createClaims(String audience){
        JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(10);
        claims.setIssuedAtToNow();
        claims.setNotBeforeMinutesInThePast(30);
        claims.setSubject(SUB_CLAIM_CONTENT);
        claims.setAudience(audience);

        return claims;
    }

    private static JwtClaims getClaimsWithWrongAudience() {
        return createClaims(AUDIENCE + "1");
    }

}
