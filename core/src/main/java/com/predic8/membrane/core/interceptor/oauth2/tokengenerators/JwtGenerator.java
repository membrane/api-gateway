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

package com.predic8.membrane.core.interceptor.oauth2.tokengenerators;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;
import org.jose4j.lang.JoseException;

import java.math.BigInteger;
import java.security.Key;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class JwtGenerator {

    public String getJwk() {
        return "{\"keys\": [ " + rsaJsonWebKey.toJson() + "]}";
    }

    public static class Claim{
        private String name;
        private String value;

        public Claim(String name, String value){
            this.setName(name);
            this.setValue(value);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    public JwtGenerator() throws JoseException {
        rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId(new BigInteger(130, random).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");
    }

    public String getSignedIdToken(String iss, String sub, String aud, int expirationInSeconds, Claim... additionalClaims) throws JoseException {
        return getSignedToken(addNonDefaultClaims(getDefaultClaims(iss, sub, aud, expirationInSeconds), additionalClaims));
    }

    private String getSignedToken(JwtClaims claims) throws JoseException {
        return prepareClaimsSigning(claims).getCompactSerialization();
    }

    private JsonWebSignature prepareClaimsSigning(JwtClaims claims) {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(rsaJsonWebKey.getPrivateKey());
        jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
        jws.setHeader("typ","JWT");
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        return jws;
    }

    private JwtClaims addNonDefaultClaims(JwtClaims claims, Claim[] additionalClaims) {
        for(Claim claim : additionalClaims)
            claims.setClaim(claim.getName(),claim.getValue());
        return claims;
    }

    private JwtClaims getDefaultClaims(String iss, String sub, String aud, float expirationInSeconds) {
        JwtClaims claims = new JwtClaims();
        claims.setIssuer(iss);
        claims.setSubject(sub);
        claims.setAudience(aud);
        claims.setExpirationTimeMinutesInTheFuture(expirationInSeconds /60f);
        claims.setIssuedAtToNow();

        claims.setGeneratedJwtId();
        claims.setNotBeforeMinutesInThePast(2);
        return claims;
    }

    public List<Claim> getClaimsFromSignedIdToken(String idToken, String iss, String aud) throws InvalidJwtException {
        ArrayList<Claim> result = new ArrayList<Claim>();
        JwtClaims claims = processIdTokenToClaims(idToken,iss,aud);

        for(String claim : claims.getClaimsMap().keySet()){
            result.add(new Claim(claim,String.valueOf(claims.getClaimValue(claim))));
        }

        return result;
    }

    public static List<Claim> getClaimsFromSignedIdToken(String idToken, String iss, String aud, Key key) throws InvalidJwtException {

        JwtClaims claims = processIdTokenToClaims(idToken,iss,aud,key);

        return getClaimsFromClaimsMap(claims);
    }

    public static List<Claim> getClaimsFromSignedIdToken(String idToken, String iss, String aud, VerificationKeyResolver resolver) throws InvalidJwtException {

        JwtClaims claims = processIdTokenToClaims(idToken,iss,aud,resolver);

        return getClaimsFromClaimsMap(claims);
    }

    public static List<Claim> getClaimsFromSignedIdToken(String idToken, String iss, String aud, String jwksUrl) throws InvalidJwtException {
        JwtClaims claims = processIdTokenToClaims(idToken,iss,aud,new HttpsJwksVerificationKeyResolver(new HttpsJwks(jwksUrl)));

        return getClaimsFromClaimsMap(claims);
    }

    public static List<Claim> getClaimsFromSignedIdToken(String idToken, String iss, String aud, String jwksUrl, AuthorizationService as) throws Exception {
        Exchange getJwks = new Request.Builder().get(jwksUrl).buildExchange();
        JwtClaims claims = processIdTokenToClaims(idToken,iss,aud,new JwksVerificationKeyResolver(new JsonWebKeySet(as.doRequest(getJwks).getBodyAsStringDecoded()).getJsonWebKeys()));

        return getClaimsFromClaimsMap(claims);
    }

    private static List<Claim> getClaimsFromClaimsMap(JwtClaims claims) {
        ArrayList<Claim> result = new ArrayList<Claim>();
        for(String claim : claims.getClaimsMap().keySet()){
            result.add(new Claim(claim,String.valueOf(claims.getClaimValue(claim))));
        }

        return result;
    }

    private JwtClaims processIdTokenToClaims(String idToken, String iss, String aud) throws InvalidJwtException {
        return processIdTokenToClaims(idToken,iss,aud,rsaJsonWebKey.getKey());
    }

    private static JwtClaims processIdTokenToClaims(String idToken, String iss, String aud, Key key) throws InvalidJwtException {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setExpectedIssuer(iss)
                .setExpectedAudience(aud)
                .setVerificationKey(key)
                .build();

        return jwtConsumer.processToClaims(idToken);
    }

    private static JwtClaims processIdTokenToClaims(String idToken, String iss, String aud, VerificationKeyResolver resolver) throws InvalidJwtException {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setExpectedIssuer(iss)
                .setExpectedAudience(aud)
                .setVerificationKeyResolver(resolver)
                .build();

        return jwtConsumer.processToClaims(idToken);
    }
}
