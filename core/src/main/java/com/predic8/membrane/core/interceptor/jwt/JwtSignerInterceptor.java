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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.json.JSONObject;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.Blob;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;

import java.io.IOException;

@MCElement(name = "jwtSigner")
public class JwtSignerInterceptor extends AbstractInterceptor {

    private JwtSessionManager.Jwk jwk;
    private RsaJsonWebKey rsaJsonWebKey;

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public void init(Router router) throws Exception {
        rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation())));
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws IOException {
       return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws IOException {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) throws IOException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(prepareJwtPayload(msg));
        jws.setKey(rsaJsonWebKey.getRsaPrivateKey());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
        try {
            msg.setBodyContent(jws.getCompactSerialization().getBytes());
            return Outcome.CONTINUE;
        } catch (JoseException e) {
            throw new RuntimeException(e);
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
