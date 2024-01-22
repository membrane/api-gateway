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

public class JwtKeyCertHandler {
    private final Key key;
    private final X509Certificate certificate;

    public JwtKeyCertHandler(Object pemObj, String pemBlock) throws IOException {
        this.key = switch (pemObj) {
            case Key k -> k;
            case KeyPair kp -> kp.getPrivate();
            default -> throw new IllegalArgumentException("Unsupported PEM type " + pemObj.getClass());
        };

        this.certificate = PEMSupport.getInstance().parseCertificate(pemBlock);
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
 }
