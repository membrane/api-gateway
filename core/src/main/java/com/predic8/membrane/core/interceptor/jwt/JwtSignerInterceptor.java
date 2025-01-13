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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "jwtSign")
public class JwtSignerInterceptor extends AbstractInterceptor {

    protected static final Logger log = LoggerFactory.getLogger("JwtSignerInterceptor");

    private JwtSessionManager.Jwk jwk;
    private RsaJsonWebKey rsaJsonWebKey;

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public void init(Router router) throws Exception {
        rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation())));
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws IOException {
       return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws IOException {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(prepareJwtPayload(exc.getMessage(flow)));
            jws.setKey(rsaJsonWebKey.getRsaPrivateKey());
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
            exc.getMessage(flow).setBodyContent(jws.getCompactSerialization().getBytes());
            return CONTINUE;
        } catch (JoseException e) {
            log.error("Error during attempt to sign JWT payload: {}", e.getLocalizedMessage());
            ProblemDetails.gateway(router.isProduction())
                    .component(getDisplayName())
                    .extension("message", e.getLocalizedMessage())
                    .detail("Error during attempt to sign JWT payload.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (IOException e) {
            log.error("Error during attempt to parse JWT payload: {}", e.getLocalizedMessage());
            ProblemDetails.gateway(router.isProduction())
                    .component(getDisplayName())
                    .extension("message", e.getLocalizedMessage())
                    .detail("Error during attempt to parse JWT payload.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private String prepareJwtPayload(Message msg) throws IOException {
        ObjectNode jsonBody = (ObjectNode) om.readTree(msg.getBodyAsStream());
        long epoch = System.currentTimeMillis() / 1000;
        jsonBody.put("iat", epoch);
        jsonBody.put("exp", epoch + 300);
        return jsonBody.toString();
    }

    public JwtSessionManager.Jwk getJwk() {
        return jwk;
    }

    @MCChildElement
    public void setJwk(JwtSessionManager.Jwk jwk) {
        this.jwk = jwk;
    }
}
