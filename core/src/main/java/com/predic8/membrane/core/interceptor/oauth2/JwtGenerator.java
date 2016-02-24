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

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class JwtGenerator {

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
    }

    public String getIdTokenSigned(String iss, String sub, String aud, int expirationInSeconds, Claim... additionalClaims) throws JoseException {

        JwtClaims claims = new JwtClaims();
        claims.setIssuer(iss);
        claims.setSubject(sub);
        claims.setAudience(aud);
        claims.setExpirationTimeMinutesInTheFuture(((float)expirationInSeconds)/60f);
        claims.setIssuedAtToNow();
        for(Claim claim : additionalClaims)
            claims.setClaim(claim.getName(),claim.getValue());
        claims.setGeneratedJwtId();
        claims.setNotBeforeMinutesInThePast(2); // not sure what this does



        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(rsaJsonWebKey.getPrivateKey());
        jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

        return jws.getCompactSerialization();
    }

    public List<Claim> getClaimsFromSignedIdToken(String iss, String aud){
        ArrayList<Claim> result = new ArrayList<Claim>();

        // TODO verify and get claims




        return result;
    }
}
