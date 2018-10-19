package com.predic8.membrane.core.interceptor.session;

import com.bornium.security.oauth2openid.token.IdTokenProvider;
import com.bornium.security.oauth2openid.token.IdTokenVerifier;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@MCElement(name = "jwtSessionManager", mixed = true)
public class JwtSessionManager extends SessionManager {

    private SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    private String key;
    private Duration validTime = Duration.ofMinutes(10);

    IdTokenProvider idTokenProvider;
    IdTokenVerifier idTokenVerifier;

    public JwtSessionManager() throws Exception {
        this(null);
    }

    public JwtSessionManager(String key) throws Exception {
        if (key == null)
            rsaJsonWebKey = generateKey();
        else
            rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(key));

        idTokenProvider = new IdTokenProvider(rsaJsonWebKey);
        idTokenVerifier = new IdTokenVerifier(idTokenProvider.getJwk());
    }

    private RsaJsonWebKey generateKey() throws JoseException {
        RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId(new BigInteger(130, random).toString(32));
        rsaJsonWebKey.setUse("sig");
        rsaJsonWebKey.setAlgorithm("RS256");
        return rsaJsonWebKey;
    }

    @Override
    protected Map<String, String> cookieValueToAttributes(String cookie) {
        //TODO jwts are immutable -> can use cache with expiration to speed this up --- Map<JWT,SESSION.CONTENT>
        try {
            return idTokenVerifier.verifyAndGetClaims(cookie);
        } catch (InvalidJwtException e) {
            log.warn("Could not verify cookie: " + cookie);
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    @Override
    protected Map<Session, String> getCookieValues(Session... session) {
        return Stream
                .of(session)
                .collect(Collectors.toMap(s -> s, s -> createJwtRepresentation(s)));
    }

    private String createJwtRepresentation(Session s) {
        try {
            return idTokenProvider.createIdToken(null, "", null, validTime, Instant.now().toString(), null, s.get());
        } catch (JoseException e) {
            throw new RuntimeException("Could not create JWT representation of session", e);
        }
    }

    @Override
    public long getExpiration(Session session) {
        return 0;
    }

    public String getKey() {
        return key;
    }

    @MCTextContent
    public void setKey(String key) {
        this.key = key;
    }
}
