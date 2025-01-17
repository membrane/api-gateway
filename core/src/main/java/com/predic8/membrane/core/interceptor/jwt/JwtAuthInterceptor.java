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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.security.*;
import org.jose4j.jwk.*;
import org.jose4j.jwt.consumer.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.EnumSet.*;
import static org.apache.commons.text.StringEscapeUtils.*;

@MCElement(name = "jwtAuth")
public class JwtAuthInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthInterceptor.class);
    public static final String ERROR_JWT_NOT_FOUND = "Could not retrieve JWT";
    public static final String ERROR_DECODED_HEADER_NOT_JSON = "JWT header is not valid JSON";
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
        name = "jwt checker.";
        setFlow(of(REQUEST));
    }

    @Override
    public void init() {
        super.init();
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

        if (kidToKey.isEmpty())
            throw new RuntimeException("No JWKs given or none resolvable - please specify at least one resolvable JWK");
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            var jwt = jwtRetriever.get(exc);
            return handleJwt(exc, jwt);
        } catch (JWTException e) {
            return setJsonErrorAndReturn(e, exc, 400, e.getMessage());
        } catch (JsonProcessingException e) {
            return setJsonErrorAndReturn(e, exc, 400, ERROR_DECODED_HEADER_NOT_JSON);
        } catch (InvalidJwtException e) {
            return setJsonErrorAndReturn(e, exc, 400, ERROR_VALIDATION_FAILED);
        } catch (Exception e) {
            return setJsonErrorAndReturn(e, exc, 400, ERROR_JWT_NOT_FOUND);
        }
    }

    public Outcome handleJwt(Exchange exc, String jwt) throws JWTException, JsonProcessingException, InvalidJwtException {
        if (jwt == null)
            throw new JWTException(ERROR_JWT_NOT_FOUND);

        var decodedJwt = new JsonWebToken(jwt);
        var kid = decodedJwt.getHeader().kid();

        if (!kidToKey.containsKey(kid)) {
            throw new JWTException(ERROR_UNKNOWN_KEY);
        }

        // we could make it possible that every key is checked instead of having the "kid" field mandatory
        // this would then need up to n checks per incoming JWT - could be a performance problem
        RsaJsonWebKey key = kidToKey.get(kid);

        Map<String, Object> jwtClaims = createValidator(key).processToClaims(jwt).getClaimsMap();

        exc.getProperties().put("jwt",jwtClaims);

        new JWTSecurityScheme(jwtClaims).add(exc);

        return CONTINUE;
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

        return jwtConsumerBuilder.build();
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
                    .body(mapper.writeValueAsString(Map.of(
                            "code", code,
                            "description", description
                            )))
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
