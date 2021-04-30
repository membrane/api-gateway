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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.jose4j.base64url.Base64Url;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@MCElement(name = "jwtAuth")
public class JwtAuthInterceptor extends AbstractInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    ObjectMapper mapper = new ObjectMapper();
    JwtRetriever jwtRetriever;
    Jwks jwks;
    String expectedAud;

    // should be used read only after init
    // Hashmap done on purpose as only here the read only thread safety is guaranteed
    volatile HashMap<String, RsaJsonWebKey> kidToKey;


    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        if(jwtRetriever == null)
            jwtRetriever = new HeaderJwtRetriever("Authorization","Bearer");

        jwks.init(router.getResolverMap(),router.getBaseLocation());

        kidToKey = jwks.getJwks().stream()
                .map(jwk -> {
                    try {
                        return new RsaJsonWebKey(mapper.readValue(jwk.getJwk(router.getResolverMap(), router.getBaseLocation(),mapper),Map.class));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(HashMap::new, (m,e) -> m.put(e.getKeyId(),e), (m1,m2) -> m1.putAll(m2));

        if(kidToKey.size() == 0)
            throw new RuntimeException("No JWKs given or none resolvable - please specify at least one resolvable JWK");
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String jwt;
        try {
            jwt = jwtRetriever.get(exc);
        }catch (Exception e){
            LOG.warn("",e);
            exc.setResponse(Response.badRequest("Could not retrieve JWT").build());
            return Outcome.RETURN;
        }

        String decode;
        try {
            decode = new String(Base64Url.decode(jwt.split(Pattern.quote("."))[0]));
        }catch (Exception e){
            LOG.warn("",e);
            exc.setResponse(Response.badRequest("JWT does not have a valid header").build());
            return Outcome.RETURN;
        }

        String kid;
        try {
            Object kidMaybe = mapper.readValue(decode, Map.class).get("kid");
            if(kidMaybe == null)
                throw new RuntimeException();

            kid = kidMaybe.toString();
        }catch (Exception e){
            LOG.warn("",e);
            exc.setResponse(Response.badRequest("JWT does not contain a kid").build());
            return Outcome.RETURN;
        }

        // we could make it possible that every key is checked instead of having the "kid" field mandatory
        // this would then need up to n checks per incoming JWT - could be a performance problem
        RsaJsonWebKey key = kidToKey.get(kid);
        if(key == null){
            LOG.warn("JWT signed by unknown key");
            exc.setResponse(Response.badRequest("JWT signed by unknown key").build());
            return Outcome.RETURN;
        }

        JwtConsumer jwtValidator = createValidator(key);

        Map<String, Object> jwtClaims;
        try {
            jwtClaims = jwtValidator.processToClaims(jwt).getClaimsMap();
        }catch (Exception e){
            LOG.warn("",e);
            exc.setResponse(Response.badRequest("JWT validation failed").build());
            return Outcome.RETURN;
        }

        exc.getProperties().put("jwt",jwtClaims);

        return Outcome.CONTINUE;
    }

    private JwtConsumer createValidator(RsaJsonWebKey key) {
        JwtConsumerBuilder jwtConsumerBuilder = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setVerificationKey(key.getRsaPublicKey());

        if(expectedAud != null && !expectedAud.isEmpty())
        jwtConsumerBuilder
                .setExpectedAudience(expectedAud);

        JwtConsumer jwtValidator = jwtConsumerBuilder.build();
        return jwtValidator;
    }

    public JwtRetriever getJwtRetriever() {
        return jwtRetriever;
    }

    @MCChildElement
    public void setJwtRetriever(JwtRetriever jwtRetriever) {
        this.jwtRetriever = jwtRetriever;
    }

    public Jwks getJwks() {
        return jwks;
    }

    @MCChildElement(order = 1)
    public void setJwks(Jwks jwks) {
        this.jwks = jwks;
    }

    public String getExpectedAud() {
        return expectedAud;
    }

    @MCAttribute
    public JwtAuthInterceptor setExpectedAud(String expectedAud) {
        this.expectedAud = expectedAud;
        return this;
    }
}
