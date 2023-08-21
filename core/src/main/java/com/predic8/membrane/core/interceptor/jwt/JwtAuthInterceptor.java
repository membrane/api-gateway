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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jose4j.base64url.Base64Url;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.InvalidJwtSignatureException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.EnumSet.of;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

@MCElement(name = "jwtAuth")
public class JwtAuthInterceptor extends AbstractInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthInterceptor.class);
    public static final String ERROR_JWT_NOT_FOUND = "Could not retrieve JWT";
    public static final String ERROR_MALFORMED_COMPACT_SERIALIZATION = "JWTs compact serialization not valid";
    public static final String ERROR_DECODED_HEADER_NOT_JSON = "JWT header is not valid JSON";
    public static final String ERROR_NO_KID_GIVEN = "JWT does not contain a kid";
    public static final String ERROR_UNKNOWN_KEY = "JWT signed by unknown key";
    public static final String ERROR_VALIDATION_FAILED = "JWT validation failed";

    ObjectMapper mapper = new ObjectMapper();
    JwtRetriever jwtRetriever;
    Jwks jwks;
    String expectedAud;

    // should be used read only after init
    // Hashmap done on purpose as only here the read only thread safety is guaranteed
    volatile HashMap<String, RsaJsonWebKey> kidToKey;

    public JwtAuthInterceptor() {
        name = "JWT Checker.";
        setFlow(of(REQUEST));
    }

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
                .collect(HashMap::new, (m,e) -> m.put(e.getKeyId(),e), HashMap::putAll);

        if(kidToKey.size() == 0)
            throw new RuntimeException("No JWKs given or none resolvable - please specify at least one resolvable JWK");
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String jwt;
        try {
            jwt = jwtRetriever.get(exc);
        }catch (Exception e){
            return setJsonErrorAndReturn(e,exc,400, ERROR_JWT_NOT_FOUND);
        }
        return handleJwt(exc, jwt);
    }

    public Outcome handleJwt(Exchange exc, String jwt) {
        if(jwt == null)
            return setJsonErrorAndReturn(null,exc,400, ERROR_JWT_NOT_FOUND);

        //

        String decode = null;
        try {
            // return JWTToken
            decode = parseJWT(jwt);
        } catch (JWTException e) {
            return setJsonErrorAndReturn(null,exc,400, ERROR_MALFORMED_COMPACT_SERIALIZATION);
        }

        String kid;

        Map map;
        try {
            map = mapper.readValue(decode, Map.class);
        }catch (Exception e){
            return setJsonErrorAndReturn(e,exc,400, ERROR_DECODED_HEADER_NOT_JSON);
        }


        try {
            Object kidMaybe = map.get("kid");
            if(kidMaybe == null)
                throw new RuntimeException();

            kid = kidMaybe.toString();
        }catch (Exception e){
            return setJsonErrorAndReturn(e,exc,400, ERROR_NO_KID_GIVEN);

        }

        // we could make it possible that every key is checked instead of having the "kid" field mandatory
        // this would then need up to n checks per incoming JWT - could be a performance problem
        RsaJsonWebKey key = kidToKey.get(kid);
        if(key == null)
            return setJsonErrorAndReturn(null,exc,400, ERROR_UNKNOWN_KEY);


        JwtConsumer jwtValidator = createValidator(key);

        Map<String, Object> jwtClaims;
        try {
            jwtClaims = jwtValidator.processToClaims(jwt).getClaimsMap();
        }catch (Exception e){
            return setJsonErrorAndReturn(e,exc,400, ERROR_VALIDATION_FAILED);
        }

        exc.getProperties().put("jwt",jwtClaims);

        return CONTINUE;
    }

    private static String parseJWT(String jwt) throws JWTException {
        String decode;
        try {
            String[] split = jwt.split(Pattern.quote("."));
            if(split.length < 3)
                throw new JWTException();

            decode = new String(Base64Url.decode(split[0]));

        }catch (Exception e){
            throw new JWTException();
        }
        return decode;
    }

    private JwtConsumer createValidator(RsaJsonWebKey key) {
        JwtConsumerBuilder jwtConsumerBuilder = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setVerificationKey(key.getRsaPublicKey());

        if (acceptAnyAud())
            jwtConsumerBuilder.setSkipDefaultAudienceValidation();
        else {
            if (expectedAud != null && !expectedAud.isEmpty())
                jwtConsumerBuilder
                        .setExpectedAudience(expectedAud);
        }


        JwtConsumer jwtValidator = jwtConsumerBuilder.build();
        return jwtValidator;
    }

    private boolean acceptAnyAud() {
        return expectedAud != null && expectedAud.equals("any!!");
    }

    private Outcome setJsonErrorAndReturn(Exception e, Exchange exc, int code, String description){
        if(e != null) {
            if (e instanceof InvalidJwtException)
                LOG.error(e.getMessage());
            else
                LOG.error("", e);
        }
        try {
            exc.setResponse(Response.ResponseBuilder.newInstance()
                    .status(code, "Bad Request")
                    .body(mapper.writeValueAsString(ImmutableMap.builder()
                            .put("code", code)
                            .put("description",description)
                            .build()))
                    .build());
        } catch (JsonProcessingException jsonProcessingException) {
            throw new RuntimeException(jsonProcessingException);
        }
        return RETURN;
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

    /**
     * @description
     * <p>Expected audience ('aud') value of the token.</p>
     * <p>Use "any!!" to allow any audience value. This is strongly discouraged.</p>
     */
    @MCAttribute
    public JwtAuthInterceptor setExpectedAud(String expectedAud) {
        this.expectedAud = expectedAud;
        return this;
    }

    @Override
    public String getShortDescription() {
        return "Checks for a valid JWT.";
    }

    @Override
    public String getLongDescription() {
        return "Checks for a valid JWT.<br/>" +
                (acceptAnyAud() ?
                        "Accepts any value for the <font style=\"font-family: monospace\">aud</font> field. <b>THIS IS STRONGLY DISCOURAGED!</b><br/>" :
                        "Accepts <font style=\"font-family: monospace\">" + escapeHtml4(expectedAud) + "</font> as valid value for the <font style=\"font-family: monospace\">aud</font> payload entry.<br/>") +
                (jwks != null ? "Validates the JWT signature against " + jwks.getLongDescription() + " ." : "");
    }

}
