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

@MCElement(name = "jwtSigner")
public class JwtSignerInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtSignerInterceptor.class.getName());

    private JwtSessionManager.Jwk jwk;
    private RsaJsonWebKey rsaJsonWebKey;

    @Override
    public void init() {
        try {
            rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation())));
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            throw new ConfigurationException("Could not init RSA JSON Web Key");
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
       return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(msg.getBodyAsStringDecoded());
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

    public JwtSessionManager.Jwk getJwk() {
        return jwk;
    }

    @MCChildElement
    public void setJwk(JwtSessionManager.Jwk jwk) {
        this.jwk = jwk;
    }
}
