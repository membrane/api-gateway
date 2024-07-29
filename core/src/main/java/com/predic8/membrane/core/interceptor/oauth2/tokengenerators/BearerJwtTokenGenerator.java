package com.predic8.membrane.core.interceptor.oauth2.tokengenerators;

import com.predic8.membrane.annot.MCElement;
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

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.NoSuchElementException;

@MCElement(name = "bearerJwtToken")
public class BearerJwtTokenGenerator implements TokenGenerator {

    private SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    public BearerJwtTokenGenerator() throws JoseException {
        rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId(new BigInteger(130, random).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");
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
}
