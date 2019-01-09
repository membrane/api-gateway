package com.predic8.membrane.core.interceptor.session;

import com.bornium.security.oauth2openid.token.IdTokenProvider;
import com.bornium.security.oauth2openid.token.IdTokenVerifier;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.security.Blob;
import com.predic8.membrane.core.exchange.Exchange;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Required;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@MCElement(name = "jwtSessionManager")
public class JwtSessionManager extends SessionManager {

    private SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    private Duration validTime = Duration.ofSeconds(expiresAfterSeconds);
    private Duration renewalTime = validTime.dividedBy(3);

    IdTokenProvider idTokenProvider;
    IdTokenVerifier idTokenVerifier;

    Jwk jwk;
    boolean verbose = false;

    public void init(Router router) throws Exception {
        if (jwk == null)
            rsaJsonWebKey = generateKey();
        else
            rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation())));

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
    protected Map<String, Object> cookieValueToAttributes(String cookie) {
        //TODO jwts are immutable -> can use cache with expiration to speed this up --- Map<JWT,SESSION.CONTENT>
        try {
            //skip signature check as it was already performed beforehand

            return idTokenVerifier.createCustomJwtValidator()
                    .setSkipSignatureVerification()
                    .build()
                    .processToClaims(cookie)
                    .getClaimsMap()

                    .entrySet()
                    .stream()
                    //.filter(entry -> !entry.getKey().equals("exp") && !entry.getKey().equals("iss")) // filter default jwt claims - those are not part of a session
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        } catch (InvalidJwtException e) {
            log.warn("Could not verify cookie: " + cookie + "\nPossible Reason: Cookie is not signed by and thus not a session of this instance");
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    @Override
    protected Map<Session, String> getCookieValues(Session... sessions) {
        return Stream
                .of(sessions)
                .collect(Collectors.toMap(s -> s, s -> createJwtRepresentation(s)));
    }

    private String createJwtRepresentation(Session s) {
        try {
            return idTokenProvider.createIdTokenNoNullClaims(issuer,null,null,validTime,null,null,s.get());
        } catch (JoseException e) {
            throw new RuntimeException("Could not create JWT representation of session", e);
        }
    }

    @Override
    public List<String> getInvalidCookies(Exchange exc, String validCookie) {
        return Stream
                .of(getAllCookieKeys(exc))
                .map(cookie -> cookie.split("=true")[0].trim())
                .filter(cookie -> {
                    try {
                        checkJwtWithoutVerifyingSignature(cookie);
                        return true;
                    } catch (InvalidJwtException e) {
                        // this should only happen if the issuer doesn't add up
                        // wrong issuer happens *all the time* so we do not want to print here to not spam the log of membrane
                        if(verbose)
                            e.printStackTrace();
                    }
                    return false;
                })
                .filter(cookie -> !cookie.equals(getKeyOfCookie(validCookie)))
                .map(cookie -> addValueToCookie(cookie))
                .collect(Collectors.toList());
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        try {
            checkJwtWithoutVerifyingSignature(cookie);
            validateSignatureOfJwt(cookie);
            return true;
        } catch (InvalidJwtException e) {
            // this should only happen if the issuer doesn't add up or the signature is malformed
            // wrong issuer happens *all the time* so we do not want to print here to not spam the log of membrane
            if(verbose)
                e.printStackTrace();
        }
        return false;
    }

    @Override
    protected boolean cookieRenewalNeeded(String cookie) {
        try {
            JwtClaims claims = checkJwtWithoutVerifyingSignature(cookie);
            return Instant.ofEpochSecond(claims.getIssuedAt().getValue()).plus(renewalTime).isBefore(Instant.now());
        } catch (InvalidJwtException e) {
            e.printStackTrace();
        } catch (MalformedClaimException e) {
            e.printStackTrace();
        }
        return false;
    }

    private JwtClaims validateSignatureOfJwt(String cookie) throws InvalidJwtException {
        return idTokenVerifier.createCustomJwtValidator()
                .setExpectedIssuer(issuer)
                .build()
                .processToClaims(cookie);
    }

    private JwtClaims checkJwtWithoutVerifyingSignature(String cookie) throws InvalidJwtException {
        return new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setExpectedIssuer(issuer)
                .setRequireExpirationTime()
                .build()
                .processToClaims(cookie);
    }

    private String addValueToCookie(String cookie) {
        return cookie + "=true";
    }

    private String getKeyOfCookie(String validCookie) {
        return validCookie.split("=true")[0];
    }

    public Jwk getJwk() {
        return jwk;
    }

    @Required
    @MCChildElement
    public void setJwk(Jwk jwk) {
        this.jwk = jwk;
    }

    @MCElement(name="jwk", mixed = true, topLevel = false, id="jwtSessionManager-jwk")
    public static class Jwk extends Blob {

    }

    public boolean isVerbose() {
        return verbose;
    }

    @MCAttribute
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
