package com.predic8.membrane.core.interceptor.oauth2.tokengenerators;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.Blob;
import com.predic8.membrane.core.interceptor.session.JwtSessionManager;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.NoSuchElementException;

@MCElement(name = "bearerJwtToken")
public class BearerJwtTokenGenerator implements TokenGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(BearerJwtTokenGenerator.class);
    private final SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    private JwtSessionManager.Jwk jwk;

    public void init(Router router) throws Exception {
        if (jwk == null) {
            rsaJsonWebKey = generateKey();
            LOG.warn("bearerJwtToken uses a generated key ('" +
                    rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE)+
                    "'). Sessions of this instance will not be compatible with sessions of other (e.g. restarted)"+
                    "instances. To solve this, write the JWK into a file and reference it using <bearerJwtToken><jwk location=\"...\">.");
        } else {
            rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation())));
        }
    }

    private RsaJsonWebKey generateKey() throws JoseException {
        RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId(new BigInteger(130, random).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");
        return rsaJsonWebKey;
    }

    @Override
    public String getTokenType() {
        return "Bearer";
    }

    @Override
    public String getToken(String username, String clientId, String clientSecret) {
        JwtClaims claims = new JwtClaims();
        claims.setSubject(username);
        claims.setClaim("clientId", clientId);
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(rsaJsonWebKey.getRsaPrivateKey());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    public JwtClaims verify(String token) throws InvalidJwtException {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setSkipDefaultAudienceValidation()
                .setVerificationKey(rsaJsonWebKey.getPublicKey())
                .build();
        return jwtConsumer.processToClaims(token);
    }

    @Override
    public String getUsername(String token) throws NoSuchElementException {
        try {
            return verify(token).getSubject();
        } catch (MalformedClaimException | InvalidJwtException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getClientId(String token) throws NoSuchElementException {
        try {
            return verify(token).getClaimValue("clientId", String.class);
        } catch (MalformedClaimException | InvalidJwtException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void invalidateToken(String token, String clientId, String clientSecret) throws NoSuchElementException {
        // not possible with JWT
        throw new IllegalStateException();
    }

    @Override
    public boolean supportsRevocation() {
        return false;
    }

    public JwtSessionManager.Jwk getJwk() {
        return jwk;
    }

    @MCChildElement
    public void setJwk(JwtSessionManager.Jwk jwk) {
        this.jwk = jwk;
    }

    @MCElement(name="jwk", mixed = true, topLevel = false, id="bearerJwtToken-jwk")
    public static class Jwk extends Blob {

    }

}
