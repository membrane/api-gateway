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
package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.http.Exchange;
import com.bornium.security.oauth2openid.providers.*;
import com.bornium.security.oauth2openid.server.ProvidedServices;
import com.bornium.security.oauth2openid.server.TokenContext;
import com.bornium.security.oauth2openid.token.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.core.interceptor.oauth2.ClientList;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MembraneProvidedServices implements ProvidedServices {

    Logger log = LoggerFactory.getLogger(MembraneProvidedServices.class);

    Cache<String,Map<String,String>> verifiedUsers = CacheBuilder
            .newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private SessionManager sessionManager;
    private ClientList clientList;
    private com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider userDataProvider;
    private String subClaimName;
    private String issuer;
    private Set<String> supportedClaims;
    private String contextPath;

    public MembraneProvidedServices(SessionManager sessionManager,
                                    ClientList clientList,
                                    com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider userDataProvider,
                                    String subClaimName,
                                    String issuer,
                                    Set<String> supportedClaims,
                                    String contextPath){
        this.sessionManager = sessionManager;
        this.clientList = clientList;
        this.userDataProvider = userDataProvider;
        this.subClaimName = subClaimName;
        this.issuer = issuer;
        this.supportedClaims = supportedClaims;
        this.contextPath = contextPath;
    }

    @Override
    public SessionProvider getSessionProvider() {
        return new SessionProvider() {
            @Override
            public Session getSession(Exchange exc) {
                com.predic8.membrane.core.exchange.Exchange memExc = Convert.convertToMembraneExchange(exc);
                com.predic8.membrane.core.interceptor.session.Session memSession = sessionManager.getSession(memExc);
                exc.getProperties().putAll(memExc.getProperties());
                return new Session() {

                    @Override
                    public String getValue(String key) throws Exception {
                        return memSession.get(key);
                    }

                    @Override
                    public void putValue(String key, String value) throws Exception {
                        memSession.put(key,value);
                    }

                    @Override
                    public void removeValue(String key) throws Exception {
                        memSession.remove(key);
                    }

                    @Override
                    public void clear() throws Exception {
                        memSession.clear();
                    }
                };
            }
        };
    }

    @Override
    public ClientDataProvider getClientDataProvider() {
        return new ClientDataProvider() {
            @Override
            public boolean clientExists(String clientId) {
                return clientList.getClient(clientId) != null;
            }

            @Override
            public boolean isConfidential(String clientId) {
                if(clientExists(clientId))
                    return clientList.getClient(clientId).getClientSecret() != null;
                return false;
            }

            @Override
            public boolean verify(String clientId, String clientSecret) {
                if(clientExists(clientId))
                    return clientList.getClient(clientId).verify(clientId,clientSecret);
                return false;
            }

            @Override
            public Set<String> getRedirectUris(String clientId) {
                if(clientExists(clientId))
                    return new HashSet<>(Arrays.asList(clientList.getClient(clientId).getCallbackUrl()));
                return new HashSet<>();
            }
        };
    }

    @Override
    public UserDataProvider getUserDataProvider() {

        return new UserDataProvider() {
            @Override
            public boolean verifyUser(String username, String password) {
                HashMap<String,String> postData = new HashMap<>();
                postData.put("username",username);
                postData.put("password",password);
                try {
                    Map<String, String> attr = userDataProvider.verify(postData);
                    verifiedUsers.put(username,attr);
                    return true;
                }catch(NoSuchElementException e){
                    return false;
                }
            }

            @Override
            public Map<String, Object> getClaims(String username, Set<String> publicClaimNames) {
                return verifiedUsers
                        .getIfPresent(username)
                        .entrySet()
                        .stream()
                        .filter(e -> publicClaimNames.contains(e.getKey()))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
            }

            @Override
            public String getSubClaim(String username) {
                return getClaims(username,new HashSet<>(Arrays.asList(subClaimName))).get(subClaimName).toString();
            }

            @Override
            public void badLogin(String username) {
                log.warn("Bad login from " + username);
            }
        };
    }

    @Override
    public TokenPersistenceProvider getTokenPersistenceProvider() {
        return new TokenPersistenceProvider() {
            @Override
            public Token createToken(String value, String username, String clientId, LocalDateTime issued, Duration validFor, String claims, String scope, String redirectUri, String nonce) {
                return new InMemoryToken(value, username, clientId, issued, validFor, claims, scope, redirectUri, nonce) {
                };
            }

            @Override
            public TokenManager createTokenManager(String tokenManagerId) {
                return new InMemoryTokenManager();
            }
        };
    }

    @Override
    public TimingProvider getTimingProvider() {
        return new DefaultTimingProvider();
    }

    @Override
    public TokenProvider getTokenProvider() {
        return new BearerTokenProvider();
    }

    @Override
    public ConfigProvider getConfigProvider() {
        return new ConfigProvider() {
            @Override
            public boolean useReusableRefreshTokens(TokenContext tokenContext) {
                return false;
            }
        };
    }

    @Override
    public String getIssuer() {
        return issuer;
    }

    @Override
    public Set<String> getSupportedClaims() {
        return supportedClaims;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getSubClaimName() {
        return "username";
    }
}
