/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.predic8.membrane.core.transport.ssl.PEMSupport;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.security.Key;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

public class JWSSigner {
    private final Key key;
    private final X509Certificate certificate;

    public JWSSigner(Object pemObj, String pemBlock) throws IOException {
        this.key = convertKey(pemObj);
        this.certificate = PEMSupport.getInstance().parseCertificate(pemBlock);
    }

    private Key convertKey(Object pemObj) {
        if (pemObj instanceof Key k) return k;
        if (pemObj instanceof KeyPair kp) return kp.getPrivate();
        throw new IllegalArgumentException("Unsupported PEM type " + pemObj.getClass());
    }

    public String generateSignedJWS(JwtClaims claims) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();

        jws.setPayload(claims.toJson());
        jws.setKey(key);
        jws.setX509CertSha1ThumbprintHeaderValue(certificate);
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setHeader("typ", "JWT");

        return jws.getCompactSerialization();
    }

    public String signToCompactSerialization(String payload) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(payload);
        jws.setKey(key);
        jws.setX509CertSha1ThumbprintHeaderValue(certificate);
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setHeader("typ", "JWT");

        return jws.getCompactSerialization();
    }
}
