package com.predic8.membrane.core.interceptor.oauth2server;

import com.bornium.http.Exchange;
import com.bornium.security.oauth2openid.providers.ClientDataProvider;
import com.bornium.security.oauth2openid.providers.Session;
import com.bornium.security.oauth2openid.providers.SessionProvider;
import com.bornium.security.oauth2openid.providers.UserDataProvider;
import com.bornium.security.oauth2openid.server.ProvidedServices;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.core.interceptor.oauth2.ClientList;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    public String getValue(String s) throws Exception {
                        return memSession.get(s);
                    }

                    @Override
                    public void putValue(String s, String s1) throws Exception {
                        memSession.put(s,s1);
                    }

                    @Override
                    public void removeValue(String s) throws Exception {
                        memSession.remove(s);
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
            public boolean clientExists(String s) {
                return clientList.getClient(s) != null;
            }

            @Override
            public boolean isConfidential(String s) {
                if(clientExists(s))
                    return clientList.getClient(s).getClientSecret() != null;
                return false;
            }

            @Override
            public boolean verify(String s, String s1) {
                if(clientExists(s))
                    return clientList.getClient(s).verify(s,s1);
                return false;
            }

            @Override
            public Set<String> getRedirectUris(String s) {
                if(clientExists(s))
                    return new HashSet<>(Arrays.asList(clientList.getClient(s).getCallbackUrl()));
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
            public Map<String, Object> getClaims(String s, Set<String> set) {
                return verifiedUsers
                        .getIfPresent(s)
                        .entrySet()
                        .stream()
                        .filter(e -> set.contains(e.getKey()))
                        .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
            }

            @Override
            public String getSubClaim(String s) {
                return getClaims(s,new HashSet<>(Arrays.asList(subClaimName))).get(subClaimName).toString();
            }

            @Override
            public void badLogin(String s) {
                log.warn("Bad login from " + s);
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
