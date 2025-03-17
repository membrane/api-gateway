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
package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import org.jetbrains.annotations.*;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.slf4j.*;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MCElement(name="jwtSessionManager2", mixed = true)
public class JwtSessionManager extends SessionManager {

    private static final Logger log = LoggerFactory.getLogger(JwtSessionManager.class);
    private final SecureRandom random = new SecureRandom();
    private RsaJsonWebKey rsaJsonWebKey;

    private String key;

    public String getKey() {
        return key;
    }

    @MCTextContent
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public void init(Router router) {
        super.init(router);

        try {
            if (key == null) {
                rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
                rsaJsonWebKey.setKeyId(new BigInteger(130, random).toString(32));
                rsaJsonWebKey.setUse("sig");
                rsaJsonWebKey.setAlgorithm("RS256");
                throw new RuntimeException("jwtSessionManager/@key is not set. Please use '" + rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE) + "'.");
            } else {
                Map<String,Object> jwkJsonParsed = JsonUtil.parseJson(key);
                rsaJsonWebKey = new RsaJsonWebKey(jwkJsonParsed);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Session validateAndReconstructSession(String singedSession) {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setVerificationKey(rsaJsonWebKey.getRsaPublicKey())
                .build();

        JwtClaims jwtClaims;
        Session session = new Session();
        try {
            jwtClaims = jwtConsumer.processToClaims(singedSession);
        } catch (InvalidJwtException e) {
            log.error("Cannot process JWT {}", e.getMessage());
            return session;
        }
        Map<String, String> attr = new HashMap<>();
        for (Map.Entry<String, List<Object>> entry : jwtClaims.flattenClaims().entrySet()) {
            if ("exp".equals(entry.getKey()))
                continue;
            if ("iat".equals(entry.getKey()))
                continue;
            if ("jti".equals(entry.getKey()))
                continue;
            if ("nbf".equals(entry.getKey()))
                continue;
            if ("sub".equals(entry.getKey())) {
                session.setUserName((String)entry.getValue().getFirst());
                continue;
            }
            if ("level".equals(entry.getKey())) {
                session.setLevel(Integer.parseInt((String)entry.getValue().getFirst()));
                continue;
            }
            if (entry.getKey().startsWith("map.")) {
                attr.put(entry.getKey().substring(4), (String)entry.getValue().getFirst());
                continue;
            }
            throw new RuntimeException("not parsed: " + entry.getKey());
        }
        session.setUserAttributes(attr);
        return session;
    }

    protected String signSession(Session session, Exchange exc) {
        String value = "";
        String userName = session.getUserName();
        if (userName != null) {

            JsonWebSignature jws = getJsonWebSignature(session, userName);

            try {
                value = jws.getCompactSerialization();
            } catch (JoseException e) {
                throw new RuntimeException(e);
            }
        }

        return getCookieValue(exc, value);
    }

    private @NotNull JsonWebSignature getJsonWebSignature(Session session, String userName) {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(getJwtClaims(session, userName).toJson());
        jws.setKey(rsaJsonWebKey.getPrivateKey());
        jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
        jws.setHeader("typ", "JWT");
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        return jws;
    }

    private @NotNull JwtClaims getJwtClaims(final Session session, String userName) {
        JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(getTimeout() / 60000f);
        claims.setIssuedAtToNow();

        claims.setGeneratedJwtId();
        claims.setNotBeforeMinutesInThePast(2);

        claims.setSubject(userName);
        claims.setStringClaim("level", "" + session.getLevel());

        synchronized (session) {
            for (Map.Entry<String, String> entry : session.getUserAttributes().entrySet()) {
                claims.setStringClaim("map." + entry.getKey(), entry.getValue());
            }
        }
        return claims;
    }

    @Override
    public void postProcess(Exchange exc) {
        Session session = exc.getPropertyOrNull(SESSION, Session.class);
        if (session != null && exc.getResponse() != null)
            exc.getResponse().getHeader().addCookieSession(getCookieName(), signSession(session, exc));
    }

    @Override
    public Session getSession(Exchange exc) {
        Session s = exc.getPropertyOrNull(SESSION, Session.class);
        if (s != null)
            return s;
        String singedSession = exc.getRequest().getHeader().getFirstCookie(getCookieName());
        if (singedSession == null) {
            return null;
        }
        Session session = validateAndReconstructSession(singedSession);
        exc.setProperty(SESSION, session);
        return session;
    }

    @Override
    public Session createSession(Exchange exc) {
        Session s = new Session();
        exc.setProperty(SESSION, s);
        return s;
    }

    @Override
    public void removeSession(Session s) {
        s.clear();
    }

    @Override
    public void removeSession(Exchange exc) {
        Session s = exc.getPropertyOrNull(SESSION, Session.class);
        if (s == null)
            return;
        s.clear();
    }

    public void cleanup() {
        // do nothing
    }
}
