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
package com.predic8.membrane.core.interceptor.authentication.xen;

import com.bornium.security.oauth2openid.Constants;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.util.*;
import org.jose4j.json.*;
import org.jose4j.jwk.*;
import org.jose4j.jws.*;
import org.jose4j.jwt.*;
import org.jose4j.jwt.consumer.*;
import org.jose4j.keys.resolvers.*;
import org.jose4j.lang.*;
import org.slf4j.*;

import java.math.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

@MCElement(name = "xenAuthentication")
public class XenAuthenticationInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(XenAuthenticationInterceptor.class);

    private String user, password;
    private UserDataProvider userDataProvider;

    private XenSessionManager sessionManager;

    @Override
    public void init() {
        super.init();
        userDataProvider.init(router);
        try {
            sessionManager.init(router);
        } catch (Exception e) {
            throw new ConfigurationException("Could not generate session manager.",e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        XenCredentialAccessor.XenLoginData login = new XenCredentialAccessor().getLogin(exc);
        if (login != null) {
            // this is a login message
            userDataProvider.verify(ImmutableMap.of("username", login.username, "password", login.password));

            login.username = user;
            login.password = password;
            new XenCredentialAccessor().replaceLogin(exc, login);
            return Outcome.CONTINUE;
        }

        // map session ids
        String ourSessionId = new XenSessionIdAccessor().getSessionId(exc, Flow.REQUEST);
        String xenSessionId = sessionManager.getXenSessionId(ourSessionId);
        if (xenSessionId == null)
            throw new RuntimeException("Session not found.");
        new XenSessionIdAccessor().replaceSessionId(exc, xenSessionId, Flow.REQUEST);

        return Outcome.CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        // map session ids
        String sessionId = new XenSessionIdAccessor().getSessionId(exc, Flow.RESPONSE);
        if (sessionId == null || sessionId.length() == 0)
            return Outcome.CONTINUE;

        String newSessionId = sessionManager.getExistingSessionId(sessionId);

        if (newSessionId == null) {
            // generate session ID mapping
            newSessionId = sessionManager.createSessionId(sessionId);
        }

        new XenSessionIdAccessor().replaceSessionId(exc, newSessionId, Flow.RESPONSE);

        return Outcome.CONTINUE;
    }

    public interface XenSessionManager {
        void init(Router router) throws Exception;
        String getXenSessionId(String ourSessionId);
        String getExistingSessionId(String xenSessionId);
        String createSessionId(String xenSessionId);
    }

    @MCElement(name = "inMemorySessionManager", topLevel = false)
    public static class InMemorySessionManager implements XenSessionManager {
        private final Map<String, String> ourSessionIds = new ConcurrentHashMap<>();
        private final Map<String, String> xenSessionIds = new ConcurrentHashMap<>();

        public void init(Router router) throws Exception {
        }

        public String getXenSessionId(String ourSessionId) {
            return ourSessionIds.get(ourSessionId);
        }

        public String getExistingSessionId(String xenSessionId) {
            return xenSessionIds.get(xenSessionId);
        }

        public String createSessionId(String xenSessionId) {
            String newSessionId = UUID.randomUUID().toString();
            xenSessionIds.put(xenSessionId, newSessionId);
            ourSessionIds.put(newSessionId, xenSessionId);
            return newSessionId;
        }
    }

    @MCElement(name = "jwtSessionManager", topLevel = false, id = "xenAuthentication-jwtSessionManager")
    public static class JwtSessionManager implements XenSessionManager {
        private String audience;

        private Jwk jwk;

        private RsaJsonWebKey rsaJsonWebKey;

        private final SecureRandom random = new SecureRandom();

        public void init(Router router) throws Exception {
            String key = jwk.get(router.getResolverMap(), router.getBaseLocation());
            if (key == null || key.length() == 0)
                rsaJsonWebKey = generateKey();
            else
                rsaJsonWebKey = new RsaJsonWebKey(JsonUtil.parseJson(key));
        }

        private RsaJsonWebKey generateKey() throws JoseException {
            RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
            rsaJsonWebKey.setKeyId(new BigInteger(130, random).toString(32));
            rsaJsonWebKey.setUse("sig");
            rsaJsonWebKey.setAlgorithm("RS256");

            LOG.warn("Using dynamically genererated key, you should write this as <jwtSessionManager ...><jwk>" + rsaJsonWebKey.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE) + "</jwk></jwtSessionManager> .");

            return rsaJsonWebKey;
        }

        public String getXenSessionId(String ourSessionId) {
            JwtConsumer consumer = new JwtConsumerBuilder()
                    .setExpectedAudience(true, audience)
                    .setVerificationKeyResolver(new JwksVerificationKeyResolver(Lists.newArrayList(rsaJsonWebKey)))
                    .build();

            try {
                JwtClaims claims = consumer.processToClaims(ourSessionId);
                return claims.getSubject();
            } catch (InvalidJwtException | MalformedClaimException e) {
                throw new RuntimeException(e);
            }
        }

        public String getExistingSessionId(String xenSessionId) {
            return null; // always issue a new JWT
        }

        public String createSessionId(String xenSessionId) {
            JwtClaims jwtClaims = createClaims(xenSessionId);

            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(jwtClaims.toJson());
            jws.setKey(rsaJsonWebKey.getPrivateKey());
            jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

            try {
                return jws.getCompactSerialization();
            } catch (JoseException e) {
                throw new RuntimeException(e);
            }
        }

        private JwtClaims createClaims(String xenSessionId) {
            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setSubject(xenSessionId);
            jwtClaims.setAudience(audience);
            jwtClaims.setIssuedAtToNow();
            NumericDate expiration = NumericDate.now();
            expiration.addSeconds(24 * 60 * 60);
            jwtClaims.setExpirationTime(expiration);
            jwtClaims.setNotBeforeMinutesInThePast(2);
            jwtClaims.setClaim(Constants.CLAIM_NONCE, random.nextLong());
            return jwtClaims;
        }

        public String getAudience() {
            return audience;
        }

        @MCAttribute
        public void setAudience(String audience) {
            this.audience = audience;
        }

        public Jwk getJwk() {
            return jwk;
        }

        @Required
        @MCChildElement
        public void setJwk(Jwk jwk) {
            this.jwk = jwk;
        }

        @MCElement(name="jwk", mixed = true, topLevel = false, id="xenAuthentication-jwtSessionManager-jwk")
        public static class Jwk extends Blob {

        }
    }

    public String getUser() {
        return user;
    }

    @MCAttribute
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    @MCAttribute
    public void setPassword(String password) {
        this.password = password;
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    @MCChildElement(order = 10)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }

    public XenSessionManager getSessionManager() {
        return sessionManager;
    }

    @MCChildElement(order = 20)
    public void setSessionManager(XenSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
}
