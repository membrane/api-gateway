/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.session;

import com.bornium.security.oauth2openid.token.*;
import com.google.common.cache.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import org.jose4j.json.*;
import org.jose4j.jwk.*;
import org.jose4j.jwt.*;
import org.jose4j.jwt.consumer.*;
import org.jose4j.lang.*;
import org.slf4j.*;

import java.math.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static org.jose4j.jwk.JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE;

/**
 * Take care - this session manager saves values in the session for internal use -> those are reserved keywords and cannot be used
 * The keywords are "iss","exp","nbf","iat".
 */
@MCElement(name = "jwtSessionManager")
public class JwtSessionManager extends SessionManager {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionManager.class);

    private Cache<Map, String> jwtCache;

    private final SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    private Duration validTime;
    private Duration renewalTime;
    private Duration jwtCacheTime = Duration.ofMinutes(2);

    IdTokenProvider idTokenProvider;
    IdTokenVerifier idTokenVerifier;

    Jwk jwk;
    boolean verbose = false;

    public void init(Router router) throws Exception {
        if (validTime == null)
            validTime = Duration.ofSeconds(expiresAfterSeconds);
        if (renewalTime == null)
            renewalTime = validTime.dividedBy(3);

        if (jwk == null) {
            rsaJsonWebKey = generateKey();
            log.warn("""
                    jwtSessionManager uses a generated key ('{}'). Sessions of this instance 
                    will not be compatible with sessions of other (e.g. restarted)
                    instances. To solve this, write the JWK into a file and reference it using <jwtSessionManager><jwk location="...">.
                    """, rsaJsonWebKey.toJson(INCLUDE_PRIVATE));
        } else {
            rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(jwk.get(router.getResolverMap(), router.getBaseLocation())));
        }

        idTokenProvider = new IdTokenProvider(rsaJsonWebKey);
        idTokenVerifier = new IdTokenVerifier(idTokenProvider.getJwk());

        jwtCache = CacheBuilder.newBuilder().expireAfterWrite(jwtCacheTime.toMillis(), TimeUnit.MILLISECONDS).build();
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
                    .processToClaims(getCookieKey(cookie))
                    .getClaimsMap()

                    .entrySet()
                    .stream()
                    //.filter(entry -> !entry.getKey().equals("exp") && !entry.getKey().equals("iss")) // filter default jwt claims - those are not part of a session
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (InvalidJwtException e) {
            log.warn("Could not verify cookie: {}\nPossible Reason: Cookie is not signed by and thus not a session of this instance", cookie);
            e.printStackTrace();
        }
        return new HashMap<>();
    }

    private String getCookieKey(String cookie) {
        return cookie.split("=")[0].trim();
    }

    @Override
    protected Map<Session, String> getCookieValues(Session... sessions) {
        return Stream
                .of(sessions)
                .collect(Collectors.toMap(s -> s, this::createJwtRepresentation));
    }

    private String createJwtRepresentation(Session s) {
        try {
            Map filteredSession = filterSession(s.get());
            String token = jwtCache.getIfPresent(filteredSession);
            if (token != null) {
                log.debug("reusing cookie for: {}", filteredSession);
                return token;
            }
            log.debug("encoding cookie: {}", filteredSession);
            token = idTokenProvider.createIdTokenNoNullClaims(issuer, null, null, validTime, null, null, new HashMap<>(filteredSession));
            jwtCache.put(filteredSession, token);
            return token;
        } catch (JoseException e) {
            throw new RuntimeException("Could not create JWT representation of session", e);
        }
    }

    private Map filterSession(Map<String, Object> stringObjectMap) {
        Map result = new HashMap(stringObjectMap);
        Stream.of("iss", "exp", "nbf", "iat").forEach(result::remove);
        return result;
    }

    @Override
    public List<String> getInvalidCookies(Exchange exc, String validCookie) {
        return Stream
                .of(getAllCookieKeys(exc))
                .map(this::getCookieKey)
                .filter(cookie -> {
                    try {
                        checkJwtWithoutVerifyingSignature(cookie);
                        return true;
                    } catch (InvalidJwtException e) {
                        // this should only happen if the issuer doesn't add up
                        // wrong issuer happens *all the time* so we do not want to print here to not spam the log of membrane
                        if (verbose)
                            e.printStackTrace();
                    }
                    return false;
                })
                .filter(cookie -> !cookie.equals(getKeyOfCookie(validCookie)))
                .map(this::addValueToCookie)
                .collect(Collectors.toList());
    }

    @Override
    protected boolean isValidCookieForThisSessionManager(String cookie) {
        try {
            cookie = getCookieKey(cookie);
            checkJwtWithoutVerifyingSignature(cookie);
            validateSignatureOfJwt(cookie);
            return true;
        } catch (InvalidJwtException e) {
            // this should only happen if the issuer doesn't add up or the signature is malformed
            // wrong issuer happens *all the time* so we do not want to print here to not spam the log of membrane
            if (verbose)
                e.printStackTrace();
        }
        return false;
    }

    @Override
    protected boolean cookieRenewalNeeded(String originalCookie) {
        try {
            JwtClaims claims = processToClaims(originalCookie);
            return Instant.ofEpochSecond(claims.getIssuedAt().getValue()).plus(renewalTime).isBefore(Instant.now());
        } catch (InvalidJwtException | MalformedClaimException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void validateSignatureOfJwt(String cookie) throws InvalidJwtException {
        idTokenVerifier.createCustomJwtValidator()
                .setExpectedIssuer(issuer)
                .build()
                .processToClaims(cookie);
    }

    private void checkJwtWithoutVerifyingSignature(String cookie) throws InvalidJwtException {
        new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setExpectedIssuer(issuer)
                .setRequireExpirationTime()
                .build()
                .processToClaims(cookie);
    }

    private JwtClaims processToClaims(String cookie) throws InvalidJwtException {
        return new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .build()
                .processToClaims(cookie);
    }

    private String addValueToCookie(String cookie) {
        return cookie + "=true";
    }

    public Jwk getJwk() {
        return jwk;
    }

    @MCChildElement
    public void setJwk(Jwk jwk) {
        this.jwk = jwk;
    }

    @MCElement(name = "jwk", mixed = true, topLevel = false, id = "jwtSessionManager-jwk")
    public static class Jwk extends Blob {

    }

    public boolean isVerbose() {
        return verbose;
    }

    @MCAttribute
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Duration getValidTime() {
        return validTime;
    }

    public void setValidTime(Duration validTime) {
        this.validTime = validTime;
    }

    public Duration getRenewalTime() {
        return renewalTime;
    }

    public void setRenewalTime(Duration renewalTime) {
        this.renewalTime = renewalTime;
    }

    public Duration getJwtCacheTime() {
        return jwtCacheTime;
    }

    public void setJwtCacheTime(Duration jwtCacheTime) {
        this.jwtCacheTime = jwtCacheTime;
    }
}
