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
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
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
import com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuthAnswerStuff;
import com.predic8.membrane.core.interceptor.oauth2client.rf.PublicUrlStuff;
import com.predic8.membrane.core.interceptor.oauth2client.rf.UserInfoHandler;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenHandler;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRefresher;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRevalidator;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.CSRFStuff.csrfTokenMatches;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.CSRFStuff.getSecurityTokenFromState;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuthAnswerStuff.isOAuth2RedirectRequest;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.UserInfoHandler.processUserInfo;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.UserInfoHandler.revalidateToken;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenManager.idTokenIsValid;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRefresher.refreshingOfAccessTokenIsNeeded;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.*;
import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;

/**
 * @description Allows only authorized HTTP requests to pass through. Unauthorized requests get a redirect to the
 * authorization server as response.
 * @topic 6. Security
 */
@MCElement(name = "oauth2Resource2")
public class OAuth2Resource2Interceptor extends AbstractInterceptorWithSession {

    private static final Logger log = LoggerFactory.getLogger(OAuth2Resource2Interceptor.class.getName());

    @GuardedBy("publicURLs")
    private final List<String> publicURLs = new ArrayList<>();
    private AuthorizationService auth;
    private OAuth2Statistics statistics;

    private int revalidateTokenAfter = -1;

    private URIFactory uriFactory;
    private boolean firstInitWhenDynamicAuthorizationService;
    private boolean initPublicURLsOnTheFly = false;
    private OriginalExchangeStore originalExchangeStore;
    private String callbackPath = "oauth2callback";

    private AccessTokenRevalidator accessTokenRevalidator = new AccessTokenRevalidator();
    private AccessTokenHandler accessTokenHandler;
    private PublicUrlStuff publicUrlStuff = new PublicUrlStuff();

    private static boolean isBearer(String auth) {
        return auth.substring(0, 7).equalsIgnoreCase("Bearer ");
    }

    public PublicUrlStuff getPublicUrlStuff() {
        return publicUrlStuff;
    }

    private final ObjectMapper om = new ObjectMapper();

    private boolean skipUserInfo;
    private JwtAuthInterceptor jwtAuthInterceptor;

    @MCChildElement
    public void setPublicUrlStuff(PublicUrlStuff publicUrlStuff) {
        this.publicUrlStuff = publicUrlStuff;
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
    public void init() throws Exception {
        super.init();
        if (originalExchangeStore == null) originalExchangeStore = new CookieOriginialExchangeStore();
    }

    @Override
    public final Outcome handleRequestInternal(Exchange exc) throws Exception {
        return handleRequestInternal2(exc);
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
            if (publicURLs.size() == 0) initPublicURLsOnTheFly = true;
            else publicURLs.replaceAll(publicUrlStuff::normalizePublicURL);
        }

        firstInitWhenDynamicAuthorizationService = getAuthService().supportsDynamicRegistration();
        if (!getAuthService().supportsDynamicRegistration()) firstInitWhenDynamicAuthorizationService = false;

        UserInfoHandler.configureJwtAuthInterceptor(router, getAuthService());
    }

    private Outcome handleRequestInternal2(Exchange exc) throws Exception {
        if (isFaviconRequest(exc)) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        Session session = getSessionManager().getSession(exc);
        OAuthAnswerStuff.simplifyMultipleOAuth2Answers(session);
        accessTokenHandler = new AccessTokenHandler(session, getAuthService());

        if (isOAuth2RedirectRequest(exc)) handleOriginalRequest(exc);

        // TODO: eigene klasse, soll austauschbar sein (MCchild element)
        if (!skipUserInfo && !session.isVerified()) {
            String auth = exc.getRequest().getHeader().getFirstValue(AUTHORIZATION);
            if (auth != null && isBearer(auth)) {
                session = getSessionManager().getSession(exc);
                session.put(ParamNames.ACCESS_TOKEN, auth.substring(7));
                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                oauth2Answer.setAccessToken(auth.substring(7));
                oauth2Answer.setTokenType("Bearer");
                Map<String, Object> userinfo = revalidateToken(oauth2Answer, statistics, getAuthService());
                if (logUserInfoIsNull(exc, userinfo)) return respondWithRedirect(exc);
                oauth2Answer.setUserinfo(userinfo);
                session.put(OAUTH2_ANSWER, oauth2Answer.serialize());
                processUserInfo(userinfo, session, getAuthService());
            }
        }

        if (session.get(OAUTH2_ANSWER) != null && accessTokenRevalidator.tokenNeedsRevalidation(session.get(ParamNames.ACCESS_TOKEN))) {
            if (revalidateToken(OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER)), statistics, getAuthService()) == null)
                session.clear();
        }

        if (session.get(OAUTH2_ANSWER) != null)
            exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER)));

        if (refreshingOfAccessTokenIsNeeded(session)) {
            synchronized (accessTokenHandler.getTokenSynchronizer(session)) {
                try {
                    AccessTokenRefresher.refreshAccessToken(session, auth);
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

        if (handleRequest(exc, publicUrlStuff.getPublicURL(exc, getAuthService(), callbackPath), session)) {
            if (exc.getResponse() == null && exc.getRequest() != null && session.isVerified() && session.get().containsKey(OAUTH2_ANSWER)) {
                exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER)));
                return Outcome.CONTINUE;
            }
            if (exc.getResponse().getStatusCode() >= 400) session.clear();
            return Outcome.RETURN;
        }

        log.debug("session present, but not verified, redirecting.");
        return respondWithRedirect(exc);
    }

    private boolean logUserInfoIsNull(Exchange exc, Map<String, Object> userinfo) {
        if (userinfo == null) {
            log.debug("userinfo is null, redirecting.");
            return true;
        }
        return false;
    }

    private void handleOriginalRequest(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
        String oa2redirect = params.get(OA2REDIRECT);

        Session session = getSessionManager().getSession(exc);

        AbstractExchange originalExchange = new ObjectMapper().readValue(session.get(oa2redictKeyNameInSession(oa2redirect)).toString(), AbstractExchangeSnapshot.class).toAbstractExchange();
        session.remove(oa2redictKeyNameInSession(oa2redirect));

        doOriginalRequest(exc, originalExchange);
    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) {
        return Outcome.CONTINUE;
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

        exc.setResponse(Response.redirect(auth.getLoginURL(state, publicUrlStuff.getPublicURL(exc, getAuthService(), callbackPath), exc.getRequestURI()), false).build());

        readBodyFromStreamIntoMemory(exc);

        Session session = getSessionManager().getSession(exc);

        originalExchangeStore.store(exc, session, state, exc);

        if (session.get().containsKey(ParamNames.STATE))
            state = session.get(ParamNames.STATE) + SESSION_VALUE_SEPARATOR + state;
        session.put(ParamNames.STATE, state);

        return Outcome.RETURN;
    }

    private String oa2redictKeyNameInSession(String oa2redirect) {
        return OA2REDIRECT_PREFIX + oa2redirect;
    }

    private void readBodyFromStreamIntoMemory(Exchange exc) {
        exc.getRequest().getBodyAsStringDecoded();
    }

    public boolean handleRequest(Exchange exc, String publicURL, Session session) throws Exception {
        String path = uriFactory.create(exc.getDestinations().getFirst()).getPath();

        if (path == null) return false;


        if (path.endsWith("/" + callbackPath)) {

            try {
                Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

                String state2 = params.get("state");

                String stateFromUri = getSecurityTokenFromState(state2);

                if (!csrfTokenMatches(session, stateFromUri)) throw new RuntimeException("CSRF token mismatch.");

                // state in session can be "merged" -> save the selected state in session overwriting the possibly merged value
                session.put(ParamNames.STATE, stateFromUri);

                AbstractExchangeSnapshot originalRequest = originalExchangeStore.reconstruct(exc, session, stateFromUri);
                String url = originalRequest.getRequest().getUri();
                if (url == null) url = "/";
                originalExchangeStore.remove(exc, session, stateFromUri);

                if (log.isDebugEnabled()) log.debug("CSRF token match.");

                String code = params.get("code");
                if (code == null) throw new RuntimeException("No code received.");

                Exchange e = getAuthService().applyAuth(new Request.Builder().post(auth.getTokenEndpoint()).contentType(APPLICATION_X_WWW_FORM_URLENCODED).header(ACCEPT, APPLICATION_JSON).header(USER_AGENT, USERAGENT), "code=" + code + "&redirect_uri=" + publicURL + callbackPath + "&grant_type=authorization_code").buildExchange();

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

                if (log.isDebugEnabled()) logi.handleResponse(e);

                if (!isJson(response)) throw new RuntimeException("Token response is no JSON.");

                @SuppressWarnings("unchecked") Map<String, Object> json = om.readValue(response.getBodyAsStreamDecoded(), Map.class);

                if (!json.containsKey("access_token")) throw new RuntimeException("No access_token received.");

                String token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"

                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();

                session.put("access_token", token); // saving for logout

                oauth2Answer.setAccessToken(token);
                oauth2Answer.setTokenType((String) json.get("token_type"));
                oauth2Answer.setExpiration(numberToString(json.get("expires_in")));
                oauth2Answer.setRefreshToken((String) json.get("refresh_token"));
                LocalDateTime now = LocalDateTime.now();
                oauth2Answer.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
                if (json.containsKey("id_token")) {
                    if (idTokenIsValid((String) json.get("id_token"), getAuthService()))
                        oauth2Answer.setIdToken((String) json.get("id_token"));
                    else oauth2Answer.setIdToken("INVALID");
                }

                // AccessTokenRevalidator.validTokens.put
                accessTokenRevalidator.getValidTokens().put(token, true);

                if (!skipUserInfo) {
                    Exchange e2 = new Request.Builder().get(auth.getUserInfoEndpoint()).header("Authorization", json.get("token_type") + " " + token).header("User-Agent", USERAGENT).header(ACCEPT, APPLICATION_JSON).buildExchange();

                    if (log.isDebugEnabled()) {
                        logi.setHeaderOnly(false);
                        logi.handleRequest(e2);
                    }

                    Response response2 = auth.doRequest(e2);

                    if (log.isDebugEnabled()) logi.handleResponse(e2);

                    if (response2.getStatusCode() != 200) {
                        statistics.accessTokenInvalid();
                        throw new RuntimeException("User data could not be retrieved.");
                    }

                    statistics.accessTokenValid();

                    if (!isJson(response2)) throw new RuntimeException("Userinfo response is no JSON.");

                    @SuppressWarnings("unchecked") Map<String, Object> json2 = om.readValue(response2.getBodyAsStreamDecoded(), Map.class);

                    oauth2Answer.setUserinfo(json2);

                    session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

                    processUserInfo(json2, session, getAuthService());
                } else {
                    session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

                    // assume access token is JWT
                    if (jwtAuthInterceptor.handleJwt(exc, token) != Outcome.CONTINUE)
                        throw new RuntimeException("Access token is not a JWT.");

                    processUserInfo((Map<String, Object>) exc.getProperty("jwt"), session, getAuthService());
                }

                doRedirect(exc, originalRequest);

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

    private void doRedirect(Exchange exc, AbstractExchangeSnapshot originalRequest) throws JsonProcessingException {
        if (originalRequest.getRequest().getMethod().equals("GET")) {
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(), false).build());
        } else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);

            Session session = getSessionManager().getSession(exc);
            session.put(oa2redictKeyNameInSession(oa2redirect), new ObjectMapper().writeValueAsString(originalRequest));


            String delimiter = originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri() + delimiter + OA2REDIRECT + "=" + oa2redirect, false).build());
        }
    }

    private void doOriginalRequest(Exchange exc, AbstractExchange originalRequest) {
        originalRequest.getRequest().getHeader().add("Cookie", exc.getRequest().getHeader().getFirstValue("Cookie"));
        exc.setRequest(originalRequest.getRequest());

        exc.getDestinations().clear();
        String xForwardedProto = originalRequest.getRequest().getHeader().getFirstValue(X_FORWARDED_PROTO);
        String xForwardedHost = originalRequest.getRequest().getHeader().getFirstValue(X_FORWARDED_HOST);
        String originalRequestUri = originalRequest.getOriginalRequestUri();
        exc.getDestinations().add(xForwardedProto + "://" + xForwardedHost + originalRequestUri);

        exc.setOriginalRequestUri(originalRequestUri);
        exc.setOriginalHostHeader(xForwardedHost);
    }


    @Override
    public String getShortDescription() {
        return "Client of the oauth2 authentication process.\n" + statistics.toString();
    }

    public OriginalExchangeStore getOriginalExchangeStore() {
        return originalExchangeStore;
    }

    @MCChildElement(order = 20, allowForeign = true)
    public void setOriginalExchangeStore(OriginalExchangeStore originalExchangeStore) {
        this.originalExchangeStore = originalExchangeStore;
    }

    public boolean isSkipUserInfo() {
        return skipUserInfo;
    }

    @MCAttribute
    public void setSkipUserInfo(boolean skipUserInfo) {
        this.skipUserInfo = skipUserInfo;
    }
}
