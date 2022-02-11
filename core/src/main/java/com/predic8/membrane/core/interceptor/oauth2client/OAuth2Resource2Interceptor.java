/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.Util;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @description Allows only authorized HTTP requests to pass through. Unauthorized requests get a redirect to the
 * authorization server as response.
 * @topic 6. Security
 */
@MCElement(name = "oauth2Resource2")
public class OAuth2Resource2Interceptor extends AbstractInterceptorWithSession {
    public static final String OAUTH2_ANSWER = "oauth2Answer";
    public static final String OA2REDIRECT = "oa2redirect";
    public static final String OA2REDIRECT_PREFIX = "_redirect_for_oa2redirect_";
    private static Logger log = LoggerFactory.getLogger(OAuth2Resource2Interceptor.class.getName());

    private final Cache<String, Object> synchronizers = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build();

    @GuardedBy("publicURLs")
    private List<String> publicURLs = new ArrayList<>();
    private AuthorizationService auth;
    private OAuth2Statistics statistics;
    private Cache<String,Boolean> validTokens = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    private int revalidateTokenAfter = -1;

    private WebServerInterceptor wsi;
    private URIFactory uriFactory;
    private boolean firstInitWhenDynamicAuthorizationService;
    private boolean initPublicURLsOnTheFly = false;
    private OriginalExchangeStore originalExchangeStore;
    private String callbackPath = "oauth2callback";

    @Override
    public void init() throws Exception {
        super.init();
        if (originalExchangeStore == null)
            originalExchangeStore = new CookieOriginialExchangeStore();
    }

    public String getPublicURL() {
        synchronized (publicURLs) {
            return String.join(" ", publicURLs);
        }
    }

    @MCAttribute
    public void setPublicURL(String publicURL) {
        synchronized (publicURLs) {
            publicURLs.clear();
            for (String url : publicURL.split("[ \t]+"))
                publicURLs.add(url);
        }
    }

    public AuthorizationService getAuthService() {
        return auth;
    }

    @Required
    @MCChildElement(order = 10)
    public void setAuthService(AuthorizationService auth) {
        this.auth = auth;
    }


    public int getRevalidateTokenAfter() {
        return revalidateTokenAfter;
    }

    /**
     * @description time in seconds until a oauth2 access token is revalidatet with authorization server. This is disabled for values &lt; 0
     * @default -1
     */
    @MCAttribute
    public void setRevalidateTokenAfter(int revalidateTokenAfter) {
        this.revalidateTokenAfter = revalidateTokenAfter;
    }

    public String getCallbackPath() {
        return callbackPath;
    }

    /**
     * @description the path used for the OAuth2 callback. ensure that it does not collide with any path used by the application
     * @default oauth2callback
     */
    @MCAttribute
    public void setCallbackPath(String callbackPath) {
        this.callbackPath = callbackPath;
    }

    @Override
    public void init(Router router) throws Exception {
        name = "OAuth 2 Client";
        setFlow(Flow.Set.REQUEST_RESPONSE);

        super.init(router);

        auth.init(router);
        statistics = new OAuth2Statistics();
        uriFactory = router.getUriFactory();

        synchronized (publicURLs) {
            if (publicURLs.size() == 0)
                initPublicURLsOnTheFly = true;
            else for (int i = 0; i < publicURLs.size(); i++)
                publicURLs.set(i, normalizePublicURL(publicURLs.get(i)));
        }

        firstInitWhenDynamicAuthorizationService = getAuthService().supportsDynamicRegistration();
        if(!getAuthService().supportsDynamicRegistration())
            firstInitWhenDynamicAuthorizationService = false;
    }

    @Override
    public final Outcome handleRequestInternal(Exchange exc) throws Exception {
        return handleRequestInternal2(exc);
    }

    private Outcome handleRequestInternal2(Exchange exc) throws Exception {
        if(isFaviconRequest(exc)){
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        Session session = getSessionManager().getSession(exc);
        simplifyMultipleOAuth2Answers(session);

        if(isOAuth2RedirectRequest(exc))
            handleOriginalRequest(exc);

        if (session == null || !session.isVerified()) {
            String auth = exc.getRequest().getHeader().getFirstValue(Header.AUTHORIZATION);
            if (auth != null && auth.substring(0, 7).equalsIgnoreCase("Bearer ")) {
                session = getSessionManager().getSession(exc);
                session.put(ParamNames.ACCESS_TOKEN, auth.substring(7));
                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                oauth2Answer.setAccessToken(auth.substring(7));
                oauth2Answer.setTokenType("Bearer");
                HashMap<String, String> userinfo = revalidateToken(oauth2Answer);
                if (userinfo == null) {
                    log.debug("userinfo is null, redirecting.");
                    return respondWithRedirect(exc);
                }
                oauth2Answer.setUserinfo(userinfo);
                session.put(OAUTH2_ANSWER,oauth2Answer.serialize());
                processUserInfo(userinfo, session);
            }
        }

        if (session == null) {
            log.debug("session is null, redirecting.");
            return respondWithRedirect(exc);
        }


        if (session.get(OAUTH2_ANSWER) != null && tokenNeedsRevalidation(session.get(ParamNames.ACCESS_TOKEN))) {
            if (revalidateToken(OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER))) == null)
                session.clear();
        }

        if(session.get(OAUTH2_ANSWER) != null)
            exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER)));

        if (refreshingOfAccessTokenIsNeeded(session)) {
            synchronized (getTokenSynchronizer(session)) {
                try {
                    refreshAccessToken(session);
                    exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER)));
                } catch (Exception e) {
                    log.warn("Failed to refresh access token, clearing session and restarting OAuth2 flow.", e);
                    session.clearAuthentication();
                }
            }
        }
        if (session.isVerified()) {
            applyBackendAuthorization(exc, session);
            statistics.successfulRequest();
            return Outcome.CONTINUE;
        }

        if (handleRequest(exc, getPublicURL(exc), session)) {
            if(exc.getResponse() == null && exc.getRequest() != null && session.isVerified() && session.get().containsKey(OAUTH2_ANSWER)) {
                exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER)));
                return Outcome.CONTINUE;
            }
            if (exc.getResponse().getStatusCode() >= 400)
                session.clear();
            return Outcome.RETURN;
        }

        log.debug("session present, but not verified, redirecting.");
        return respondWithRedirect(exc);
    }

    /**
     * Tries to avoid very long cookies by dropping all OAUTH2_ANSWERS except the first one.
     *
     * (The SessionManager.mergeCookies produces a value with "{...answer1...},{...answer2...}".
     * We locate the ',' in between the JSON objects and split the string.)
     */
    private void simplifyMultipleOAuth2Answers(Session session) {
        if (session == null)
            return;
        String answer = session.get(OAUTH2_ANSWER);
        if (answer == null)
            return;
        int indexOfTopLevelComma = getIndexOfTopLevelComma(answer);
        if (indexOfTopLevelComma == -1)
            return;
        answer = answer.substring(0, indexOfTopLevelComma);
        session.put(OAUTH2_ANSWER, answer);
    }

    private int getIndexOfTopLevelComma(String answer) {
        int curlyBraceLevel = 0;
        boolean inString = false;
        boolean escapeNext = false;
        for (int i = 0; i < answer.length(); i++) {
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            char c = answer.charAt(i);
            if (inString) {
                switch (c) {
                    case '\"':
                        inString = false;
                        break;
                    case '\\':
                        escapeNext = true;
                        break;
                }
            } else {
                switch (c) {
                    case '{':
                        curlyBraceLevel++;
                        break;
                    case '}':
                        curlyBraceLevel--;
                        break;
                    case ',':
                        if (curlyBraceLevel == 0)
                            return i;
                        break;
                    case '"':
                        inString = true;
                        break;
                }
            }
        }
        return -1;
    }

    private Object getTokenSynchronizer(Session session) {
        OAuth2AnswerParameters oauth2Params = null;
        try {
            oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String rt = oauth2Params.getRefreshToken();
        if (rt == null)
            return new Object();

        try {
            return synchronizers.get(rt, () -> new Object());
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleOriginalRequest(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);
        String oa2redirect = params.get(OA2REDIRECT);

        Session session = getSessionManager().getSession(exc);

        AbstractExchange originalExchange = new ObjectMapper().readValue(session.get(oa2redictKeyNameInSession(oa2redirect)).toString(),AbstractExchangeSnapshot.class).toAbstractExchange();
        session.remove(oa2redictKeyNameInSession(oa2redirect));

        doOriginalRequest(exc,originalExchange);
    }

    private boolean isOAuth2RedirectRequest(Exchange exc) {
        return exc.getOriginalRequestUri().contains(OA2REDIRECT);
    }

    private void refreshAccessToken(Session session) throws Exception {

        if(!refreshingOfAccessTokenIsNeeded(session))
            return;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        Exchange refreshTokenExchange = new Request.Builder()
                .post(auth.getTokenEndpoint())
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(Header.ACCEPT, "application/json")
                .header(Header.USER_AGENT, Constants.USERAGENT)
                .header(Header.AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((auth.getClientId() + ":" + auth.getClientSecret()).getBytes())))
                .body("&grant_type=refresh_token"
                        + "&refresh_token=" + oauth2Params.getRefreshToken())
                .buildExchange();

        Response refreshTokenResponse = auth.doRequest(refreshTokenExchange);
        if (!refreshTokenResponse.isOk()) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode from authorization server for refresh token request: " + refreshTokenResponse.getStatusCode());
        }

        HashMap<String, String> json = Util.parseSimpleJSONResponse(refreshTokenResponse);
        if (json.get("access_token") == null || json.get("refresh_token") == null) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + refreshTokenResponse.getStatusCode());
        }
        oauth2Params.setAccessToken(json.get("access_token"));
        oauth2Params.setRefreshToken(json.get("refresh_token"));
        oauth2Params.setExpiration(json.get("expires_in"));
        LocalDateTime now = LocalDateTime.now();
        oauth2Params.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (idTokenIsValid(json.get("id_token")))
                oauth2Params.setIdToken(json.get("id_token"));
            else
                oauth2Params.setIdToken("INVALID");
        }

        session.put(OAUTH2_ANSWER, oauth2Params.serialize());

    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session) throws IOException {
        if(session.get(OAUTH2_ANSWER) == null)
            return false;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        if(oauth2Params.getRefreshToken() == null || oauth2Params.getRefreshToken().isEmpty() || oauth2Params.getExpiration() == null || oauth2Params.getExpiration().isEmpty())
            return false;

        return LocalDateTime.now().isAfter(oauth2Params.getReceivedAt().plusSeconds(Long.parseLong(oauth2Params.getExpiration())).minusSeconds(5)); // refresh token 5 seconds before expiration
    }

    private HashMap<String, String> revalidateToken(OAuth2AnswerParameters params) throws Exception {

        Exchange e2 = new Request.Builder()
                .get(auth.getUserInfoEndpoint())
                .header("Authorization", params.getTokenType() + " " + params.getAccessToken())
                .header("User-Agent", Constants.USERAGENT)
                .header(Header.ACCEPT, "application/json")
                .buildExchange();

        Response response2 = auth.doRequest(e2);


        if (response2.getStatusCode() != 200) {
            statistics.accessTokenInvalid();
            return null;
        } else {
            statistics.accessTokenValid();
            return Util.parseSimpleJSONResponse(response2);
        }
    }

    private boolean tokenNeedsRevalidation(String token) {
        if(revalidateTokenAfter < 0)
            return false;
        return validTokens.getIfPresent(token) == null;

    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) throws Exception {
        return Outcome.CONTINUE;
    }

    private String getPublicURL(Exchange exc) throws Exception {
        String xForwardedProto = exc.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_PROTO);
        boolean isHTTPS = xForwardedProto != null ? "https".equals(xForwardedProto) : exc.getRule().getSslInboundContext() != null;
        String publicURL = (isHTTPS ? "https://" : "http://") + exc.getOriginalHostHeader();
        RuleKey key = exc.getRule().getKey();
        if (!key.isPathRegExp() && key.getPath() != null)
            publicURL += key.getPath();
        publicURL = normalizePublicURL(publicURL);

        synchronized (publicURLs) {
            if (publicURLs.contains(publicURL))
                return publicURL;
            if (!initPublicURLsOnTheFly)
                return publicURLs.get(0);
        }

        String newURL = null;
        if(initPublicURLsOnTheFly)
            newURL = addPublicURL(publicURL);

        if(firstInitWhenDynamicAuthorizationService && newURL != null)
            getAuthService().dynamicRegistration(getPublicURLs().stream().map(url -> url + callbackPath).collect(Collectors.toList()));

        return publicURL;
    }

    /**
     * @return the new public URL, if a new one was added. null if the URL is not new.
     */
    private String addPublicURL(String publicURL) {
        synchronized (publicURLs) {
            if (publicURLs.contains(publicURL))
                return null;
            publicURLs.add(publicURL);
        }
        return publicURL;
    }

    private List<String> getPublicURLs() {
        synchronized(publicURLs) {
            return new ArrayList<>(publicURLs);
        }
    }

    private String normalizePublicURL(String url) {
        if(!url.endsWith("/"))
            url += "/";
        return url;
    }

    private boolean isFaviconRequest(Exchange exc) {
        return exc.getRequestURI().startsWith("/favicon.ico");
    }

    private void applyBackendAuthorization(Exchange exc, Session s) {
        Header h = exc.getRequest().getHeader();
        for (Map.Entry<String, Object> e : s.get().entrySet())
            if (e.getKey().startsWith("header")) {
                String headerName = e.getKey().substring(6);
                h.removeFields(headerName);
                h.add(headerName, e.getValue().toString());
            }

    }

    private Outcome respondWithRedirect(Exchange exc) throws Exception {
        String state = new BigInteger(130, new SecureRandom()).toString(32);

        exc.setResponse(Response.redirect(auth.getLoginURL(state, getPublicURL(exc) + callbackPath, exc.getRequestURI()),false).build());

        readBodyFromStreamIntoMemory(exc);

        Session session = getSessionManager().getSession(exc);

        originalExchangeStore.store(exc, session, state, exc);

        if(session.get().containsKey(ParamNames.STATE))
            state = session.get(ParamNames.STATE) + SessionManager.SESSION_VALUE_SEPARATOR + state;
        session.put(ParamNames.STATE,state);

        return Outcome.RETURN;
    }

    private String oa2redictKeyNameInSession(String oa2redirect) {
        return OA2REDIRECT_PREFIX + oa2redirect;
    }

    private void readBodyFromStreamIntoMemory(Exchange exc) {
        exc.getRequest().getBodyAsStringDecoded();
    }

    public boolean handleRequest(Exchange exc, String publicURL, Session session) throws Exception {
        String path = uriFactory.create(exc.getDestinations().get(0)).getPath();

        if(path == null)
            return false;


        if(path.endsWith("/" + callbackPath)) {

            try {
                Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);

                String state2 = params.get("state");

                String stateFromUri = getSecurityTokenFromState(state2);

                if(!csrfTokenMatches(session, stateFromUri))
                    throw new RuntimeException("CSRF token mismatch.");

                // state in session can be "merged" -> save the selected state in session overwriting the possibly merged value
                session.put(ParamNames.STATE,stateFromUri);

                AbstractExchangeSnapshot originalRequest = originalExchangeStore.reconstruct(exc, session, stateFromUri);
                String url = originalRequest.getRequest().getUri();
                if (url == null)
                    url = "/";
                originalExchangeStore.remove(exc, session, stateFromUri);

                if (log.isDebugEnabled())
                    log.debug("CSRF token match.");

                String code = params.get("code");
                if (code == null)
                    throw new RuntimeException("No code received.");

                Exchange e = new Request.Builder()
                        .post(auth.getTokenEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .header(Header.ACCEPT, "application/json")
                        .header(Header.USER_AGENT, Constants.USERAGENT)
                        .header(Header.AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((auth.getClientId() + ":" + auth.getClientSecret()).getBytes())))
                        .body("code=" + code
                                + "&redirect_uri=" + publicURL + callbackPath
                                + "&grant_type=authorization_code")
                        .buildExchange();

                LogInterceptor logi = null;
                if (log.isDebugEnabled()) {
                    logi = new LogInterceptor();
                    logi.setHeaderOnly(false);
                    logi.handleRequest(e);
                }

                Response response = auth.doRequest(e);

                if (response.getStatusCode() != 200) {
                    response.getBody().read();
                    throw new RuntimeException("Authorization server returned " + response.getStatusCode() + ".");
                }

                if (log.isDebugEnabled())
                    logi.handleResponse(e);

                HashMap<String, String> json = Util.parseSimpleJSONResponse(response);

                if (!json.containsKey("access_token"))
                    throw new RuntimeException("No access_token received.");

                String token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"

                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();

                session.put("access_token",token); // saving for logout

                oauth2Answer.setAccessToken(token);
                oauth2Answer.setTokenType(json.get("token_type"));
                oauth2Answer.setExpiration(json.get("expires_in"));
                oauth2Answer.setRefreshToken(json.get("refresh_token"));
                LocalDateTime now = LocalDateTime.now();
                oauth2Answer.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
                if(json.containsKey("id_token")) {
                    if (idTokenIsValid(json.get("id_token")))
                        oauth2Answer.setIdToken(json.get("id_token"));
                    else
                        oauth2Answer.setIdToken("INVALID");
                }

                validTokens.put(token,true);

                Exchange e2 = new Request.Builder()
                        .get(auth.getUserInfoEndpoint())
                        .header("Authorization", json.get("token_type") + " " + token)
                        .header("User-Agent", Constants.USERAGENT)
                        .header(Header.ACCEPT, "application/json")
                        .buildExchange();

                if (log.isDebugEnabled()) {
                    logi.setHeaderOnly(false);
                    logi.handleRequest(e2);
                }

                Response response2 = auth.doRequest(e2);

                if (log.isDebugEnabled())
                    logi.handleResponse(e2);

                if (response2.getStatusCode() != 200) {
                    statistics.accessTokenInvalid();
                    throw new RuntimeException("User data could not be retrieved.");
                }

                statistics.accessTokenValid();

                HashMap<String, String> json2 = Util.parseSimpleJSONResponse(response2);

                oauth2Answer.setUserinfo(json2);

                session.put(OAUTH2_ANSWER,oauth2Answer.serialize());

                processUserInfo(json2, session);

                doRedirect(exc,originalRequest);

                originalExchangeStore.postProcess(exc);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                exc.setResponse(Response.badRequest().body(e.getMessage()).build());
                originalExchangeStore.postProcess(exc);
                return true;
            }
        }
        return false;
    }

    private String getSecurityTokenFromState(String state2) {
        if (state2 == null)
            throw new RuntimeException("No CSRF token.");

        Map<String, String> param = URLParamUtil.parseQueryString(state2);

        if (param == null || !param.containsKey("security_token"))
            throw new RuntimeException("No CSRF token.");

        return param.get("security_token");
    }

    private boolean csrfTokenMatches(Session session, String state2) {
        Optional<Object> sessionRaw = Optional.ofNullable(session.get(ParamNames.STATE));
        if(!sessionRaw.isPresent())
            return false;
        return Arrays
                .asList(sessionRaw.get().toString().split(SessionManager.SESSION_VALUE_SEPARATOR))
                .stream()
                .filter(s -> s.equals(state2))
                .count() == 1;
    }

    private void doRedirect(Exchange exc, AbstractExchangeSnapshot originalRequest) throws JsonProcessingException {
        if(originalRequest.getRequest().getMethod().equals("GET")){
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(),false).build());
        }else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);

            Session session = getSessionManager().getSession(exc);
            session.put(oa2redictKeyNameInSession(oa2redirect),new ObjectMapper().writeValueAsString(originalRequest));


            String delimiter = originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri() + delimiter + OA2REDIRECT + "=" + oa2redirect, false).build());
        }
    }

    private void doOriginalRequest(Exchange exc, AbstractExchange originalRequest) throws Exception {
        originalRequest.getRequest().getHeader().add("Cookie",exc.getRequest().getHeader().getFirstValue("Cookie"));
        exc.setRequest(originalRequest.getRequest());

        exc.getDestinations().clear();
        String xForwardedProto = originalRequest.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_PROTO);
        String xForwardedHost = originalRequest.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_HOST);
        String originalRequestUri = originalRequest.getOriginalRequestUri();
        exc.getDestinations().add(xForwardedProto + "://" + xForwardedHost + originalRequestUri);

        exc.setOriginalRequestUri(originalRequestUri);
        exc.setOriginalHostHeader(xForwardedHost);
    }

    private void processUserInfo(Map<String, String> userInfo, Session session) {
        if (!userInfo.containsKey(auth.getSubject()))
            throw new RuntimeException("User object does not contain " + auth.getSubject() + " key.");

        Map<String, Object> userAttributes = session.get();
        String userIdPropertyFixed = auth.getSubject().substring(0, 1).toUpperCase() + auth.getSubject().substring(1);
        String username = userInfo.get(auth.getSubject());
        userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, username);

        session.authorize(username);
    }

    private boolean idTokenIsValid(String idToken) throws Exception {
        //TODO maybe change this to return claims and also save them in the oauth2AnswerParameters
        try {
            JwtGenerator.getClaimsFromSignedIdToken(idToken, getAuthService().getIssuer(), getAuthService().getClientId(), getAuthService().getJwksEndpoint(), auth);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    @Override
    public String getShortDescription() {
        return "Client of the oauth2 authentication process.\n" + statistics.toString();
    }

    public OriginalExchangeStore getOriginalExchangeStore() {
        return originalExchangeStore;
    }

    @MCChildElement(order=20, allowForeign = true)
    public void setOriginalExchangeStore(OriginalExchangeStore originalExchangeStore) {
        this.originalExchangeStore = originalExchangeStore;
    }
}
