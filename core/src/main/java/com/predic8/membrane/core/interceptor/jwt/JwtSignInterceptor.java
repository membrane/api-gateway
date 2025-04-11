/*
 * Copyright 2025 predic8 GmbH, www.predic8.com
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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.util.*;
import org.jose4j.json.*;
import org.jose4j.jwk.*;
import org.jose4j.jws.*;
import org.jose4j.lang.*;
import org.slf4j.*;

import java.io.*;
import java.util.Map;
import java.util.Objects;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.jose4j.jws.AlgorithmIdentifiers.*;

@MCElement(name = "jwtSign")
public class JwtSignInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtSignInterceptor.class);
    private static final String DEFAULT_PKEY = "wP1ITsKxAWOO03eywdOj73T5Po1OFXZlFgzf1CJaf8D3piuxS0C5JSHTKTO354_r9cg2WyQ3nEqJ6YScV3-NfW8xXbiyMr5Xokzn7YpuB9dtby0veEn4w7JHChH5lV2fwrjH2iL6IIOLrND9D_Dxoc3mmLMaie0mTW9-UHGOunk";

    private JwtSessionManager.Jwk jwk;
    private RsaJsonWebKey rsaJsonWebKey;
    private int expirySeconds = 300;
    private int clockSkewSeconds = 120;

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public void init() {
        super.init();
        try {
            Map<String, Object> params = JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation()));
            if (Objects.equals(params.get("p"), DEFAULT_PKEY)) {
                log.warn("""
                    \n------------------------------------ DEFAULT JWK IN USE! ------------------------------------
                            This key is for demonstration purposes only and UNSAFE for production use.          \s
                    ---------------------------------------------------------------------------------------------""");
            }
            rsaJsonWebKey = new RsaJsonWebKey(params);
        } catch (JoseException e) {
            throw new ConfigurationException("Cannot create RSA JSON Web Key",e);
        } catch (IOException e) {
            throw new ConfigurationException("Cannot parse JWK",e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
       return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(prepareJwtPayload(exc.getMessage(flow)));
            jws.setKey(rsaJsonWebKey.getRsaPrivateKey());
            jws.setAlgorithmHeaderValue(RSA_USING_SHA256);
            jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
            exc.getMessage(flow).setBodyContent(jws.getCompactSerialization().getBytes());
            return CONTINUE;
        } catch (Exception e) {
            log.error("Error during attempt to sign JWT payload", e);
            security(router.isProduction(),getDisplayName())
                    .addSubSee("crypto")
                    .detail("Error during attempt to sign JWT payload.")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private String prepareJwtPayload(Message msg) throws IOException {
        ObjectNode jsonBody = (ObjectNode) om.readTree(msg.getBodyAsStream());
        long epoch = System.currentTimeMillis() / 1000;
        jsonBody.put("iat", epoch);
        jsonBody.put("exp", epoch + expirySeconds);
        jsonBody.put("nbf", epoch - clockSkewSeconds);
        return jsonBody.toString();
    }

    public JwtSessionManager.Jwk getJwk() {
        return jwk;
    }

    @MCChildElement
    public void setJwk(JwtSessionManager.Jwk jwk) {
        this.jwk = jwk;
    }

    public int getExpirySeconds() {
        return expirySeconds;
    }

    /**
     * Time in seconds the token will expire after.
     * @default 300
     */
    @MCAttribute
    public void setExpirySeconds(int expirySeconds) {
        this.expirySeconds = expirySeconds;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    /**
     * To account for clock skew between different systems, the beginning of the validity time period (nbf - "not before")
     * will be set to a point in time in the past. This point in time will be "issued at" - "clock skew".
     * @param clockSkewSeconds
     */
    @MCAttribute
    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
