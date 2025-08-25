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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.oauth2.ParamNames.*;
import static com.predic8.membrane.core.interceptor.oauth2client.LoginParameter.copyLoginParameters;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuthUtils.*;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.*;
import static com.predic8.membrane.core.interceptor.session.SessionManager.*;
import static java.net.URLEncoder.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Allows only authorized HTTP requests to pass through. Unauthorized requests get a redirect to the
 * authorization server as response.
 * @topic 3. Security and Validation
 */
@MCElement(name = "oauth2Resource2")
public class OAuth2Resource2Interceptor extends AbstractInterceptorWithSession {

    private static final Logger log = LoggerFactory.getLogger(OAuth2Resource2Interceptor.class.getName());
    public static final String ERROR_STATUS = "oauth2-error-status";
    public static final String EXPECTED_AUDIENCE = "oauth2-expected-audience";
    public static final String WANTED_SCOPE = "oauth2-wanted-scope";

    private AuthorizationService auth;
    private OAuth2Statistics statistics;
    private URIFactory uriFactory;
    private OriginalExchangeStore originalExchangeStore;
    private String callbackPath = "oauth2callback";

    private final AccessTokenRevalidator accessTokenRevalidator = new AccessTokenRevalidator();
    private final AccessTokenRefresher accessTokenRefresher = new AccessTokenRefresher();
    private PublicUrlManager publicUrlManager = new PublicUrlManager();
    private final SessionAuthorizer sessionAuthorizer = new SessionAuthorizer();
    private final OAuth2CallbackRequestHandler oAuth2CallbackRequestHandler = new OAuth2CallbackRequestHandler();
    private final TokenAuthenticator tokenAuthenticator = new TokenAuthenticator();
    private String customHeaderUserPropertyPrefix;
    private String logoutUrl;
    private String afterLogoutUrl = "/";
    private String afterErrorUrl = null;
    private List<LoginParameter> loginParameters = new ArrayList<>();
    private boolean appendAccessTokenToRequest;
    private boolean onlyRefreshToken = false;

    @Override
    public void init() {
        super.init();
        name = "oauth2 client";
        setFlow(Flow.Set.REQUEST_RESPONSE_ABORT_FLOW);

        if (originalExchangeStore == null) {
            originalExchangeStore = new CookieOriginialExchangeStore();
        }
        try {
            auth.init(router);
        } catch (Exception e) {
            log.error("", e);
            throw new ConfigurationException("Could not init auth in OAuth2Resource2Interceptor",e);
        }
        statistics = new OAuth2Statistics();
        uriFactory = router.getUriFactory();

        publicUrlManager.init(auth, callbackPath);
        accessTokenRevalidator.init(auth, statistics);
        accessTokenRefresher.init(auth, onlyRefreshToken);
        sessionAuthorizer.init(auth, router, statistics);

        oAuth2CallbackRequestHandler.init(uriFactory, auth, originalExchangeStore, accessTokenRevalidator,
                sessionAuthorizer, publicUrlManager, callbackPath, onlyRefreshToken);
        tokenAuthenticator.init(sessionAuthorizer, statistics, accessTokenRevalidator, auth);


    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) {
        return CONTINUE;
    }

    @Override
    public final Outcome handleRequestInternal(Exchange exc) throws Exception {

        Session session = getSessionManager().getSession(exc);

        if (isLogoutBackRequest(exc)) {
            exc.setResponse(Response.redirect(afterLogoutUrl, 303).build());
            logOutSession(exc);
            return RETURN;
        }

        if (isLogoutRequest(exc)) {
            exc.setResponse(Response.redirect(getLogoutRedirectUri(exc, session), 303).build());
            logOutSession(exc);
            return RETURN;
        }

        if (isFaviconRequest(exc)) {
            exc.setResponse(Response.badRequest().build());
            return RETURN;
        }

        OAuthUtils.simplifyMultipleOAuth2Answers(session);

        if (isOAuth2RedirectRequest(exc)) {
            handleOriginalRequest(exc);
        }

        String wantedScope = exc.getProperty(WANTED_SCOPE, String.class);
        if (tokenAuthenticator.userInfoIsNullAndShouldRedirect(session, exc, wantedScope)) {
            return respondWithRedirect(exc, FlowContext.fromExchange(exc));
        }

        accessTokenRevalidator.revalidateIfNeeded(session, wantedScope);

        if (session.hasOAuth2Answer(wantedScope)) {
            exc.setProperty(Exchange.OAUTH2, session.getOAuth2AnswerParameters(wantedScope));
        }

        accessTokenRefresher.refreshIfNeeded(session, exc);

        try {
            if (wasCallback(exc)) {
                oAuth2CallbackRequestHandler.handleRequest(exc, session);
                if (exc.getResponse().getStatusCode() >= 400) {
                    session.clear();
                }
                return RETURN;
            }
            if (session.isVerified()) {
                applyBackendAuthorization(exc, session);
                statistics.successfulRequest();
                appendAccessTokenToRequest(exc);
                return CONTINUE;
            }

            log.debug("session present, but not verified, redirecting.");
            return respondWithRedirect(exc, FlowContext.fromExchange(exc));
        } catch (OAuth2Exception e) {
            session.clear();
            if (afterErrorUrl != null) {
                FormPostGenerator fpg = new FormPostGenerator(afterErrorUrl).withParameter("error", e.getError());
                if (e.getErrorDescription() != null)
                    fpg.withParameter("error_description", e.getErrorDescription());
                exc.setResponse(fpg.build());
            } else {
                exc.setResponse(e.getResponse());
            }
            return RETURN;
        }
    }

    private @NotNull String getLogoutRedirectUri(Exchange exc, Session session) throws Exception {
        String endSessionEndpoint = auth.getEndSessionEndpoint();
        if (endSessionEndpoint == null || session.getOAuth2Answer(null) == null) {
            return afterLogoutUrl;
        }
        String redirectUri = replaceUrlPath(publicUrlManager.getPublicURLAndReregister(exc), logoutUrl + "/back");
        String uri = endSessionEndpoint + "?post_logout_redirect_uri=" + encode(redirectUri, UTF_8);

        OAuth2AnswerParameters ap = session.getOAuth2AnswerParameters();
        if (ap != null && ap.getIdToken() != null)
            uri += "&id_token_hint=" + ap.getIdToken();
        return uri;
    }

    private String replaceUrlPath(String url, String newPath) {
        URI uri = router.getUriFactory().createWithoutException(url);
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "") + newPath;
    }

    private void handleOriginalRequest(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
        String oa2redirect = params.get(OA2REDIRECT);

        Session session = getSessionManager().getSession(exc);

        AbstractExchange originalExchange = new ObjectMapper().readValue(
                        session.get(OAuthUtils.oa2redictKeyNameInSession(oa2redirect)).toString(),
                        AbstractExchangeSnapshot.class)
                .toAbstractExchange();
        session.remove(OAuthUtils.oa2redictKeyNameInSession(oa2redirect));

        doOriginalRequest(exc, originalExchange);
    }

    private boolean isLogoutRequest(Exchange exc) {
        return logoutUrl != null && exc.getRequestURI().startsWith(logoutUrl);
    }

    private boolean isLogoutBackRequest(Exchange exc) {
        return logoutUrl != null && exc.getRequestURI().startsWith(logoutUrl + "/back");
    }

    public void logOutSession(Exchange exc) {
        Session session = getSessionManager().getSession(exc);

        session.clear();
        getSessionManager().removeSession(exc);
        exc.getProperties().remove(SESSION);
        exc.getProperties().remove(SESSION_COOKIE_ORIGINAL);
    }

    private boolean isFaviconRequest(Exchange exc) {
        return exc.getRequestURI().startsWith("/favicon.ico");
    }

    private void applyBackendAuthorization(Exchange exc, Session s) {
        if (customHeaderUserPropertyPrefix == null)
            return;
        Header h = exc.getRequest().getHeader();
        for (Map.Entry<String, Object> e : s.get().entrySet()) {
            if (e.getKey().startsWith(customHeaderUserPropertyPrefix)) {
                String headerName = e.getKey().substring(customHeaderUserPropertyPrefix.length());
                h.removeFields(headerName);
                h.add(headerName, e.getValue().toString());
            }
        }
    }

    public Outcome respondWithRedirect(Exchange exc, FlowContext flowContext) throws Exception {
        Integer errorStatus = exc.getProperty(ERROR_STATUS, Integer.class);
        if (errorStatus != null) {
            exc.setResponse(Response.statusCode(errorStatus).header(CONTENT_LENGTH, "0").build());
            return RETURN;
        }

        PKCEVerifier verifier = new PKCEVerifier();
        StateManager stateManager = new StateManager(verifier, flowContext);
        Response redirectResponse = Response
                .redirect(auth.getLoginURL(publicUrlManager.getPublicURLAndReregister(exc) + callbackPath)
                        + stateManager.buildStateParameter(exc)
                        + verifier.getUrlParams()
                        + copyLoginParameters(exc, getLoginParametersToPassAlong(exc)), 302)
                .build();

        exc.setResponse(redirectResponse); // The session MUST be created AFTER the response has been overwritten.

        readBodyFromStreamIntoMemory(exc);

        Session session = getSessionManager().getSession(exc);

        originalExchangeStore.store(exc, session, stateManager, exc);

        stateManager.saveToSession(session);
        verifier.saveToSession(session);

        return RETURN;
    }

    private @NotNull List<LoginParameter> getLoginParametersToPassAlong(Exchange exc) {
        Map<String, String> lps = loginParameters.stream()
                .collect(HashMap::new, (m, lp) -> m.put(lp.getName(), lp.getValue()), HashMap::putAll);
        Optional.ofNullable((List<LoginParameter>) exc.getProperty("loginParameters")).orElse(List.of())
                .forEach(lp -> lps.put(lp.getName(), lp.getValue()));

        var combinedLoginParameters = lps.entrySet().stream()
                .filter(e -> {
                    String key = e.getKey();
                    return !"client_id".equals(key) && !"response_type".equals(key) && !"scope".equals(key)
                            && !"redirect_uri".equals(key) && !"response_mode".equals(key) && !STATE.equals(key)
                            && !"claims".equals(key);
                })
                .map(e ->
                        new LoginParameter(e.getKey(), e.getValue())
                ).toList();
        return combinedLoginParameters;
    }


    private void readBodyFromStreamIntoMemory(Exchange exc) {
        exc.getRequest().getBodyAsStringDecoded();
    }

    private boolean wasCallback(Exchange exc) throws Exception {
        String path = uriFactory.create(exc.getDestinations().getFirst()).getPath();
        if (path == null) {
            return false;
        }
        return path.endsWith("/" + callbackPath);
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

    private void appendAccessTokenToRequest(Exchange exc) {
        if (!appendAccessTokenToRequest)
            return;
        if (exc.getProperty(OAUTH2) == null)
            return;
        OAuth2AnswerParameters params = exc.getProperty(OAUTH2, OAuth2AnswerParameters.class);
        if (params.getAccessToken() == null)
            return;
        exc.getRequest().getHeader().setValue(AUTHORIZATION, "Bearer " + params.getAccessToken());
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
        return sessionAuthorizer.isSkipUserInfo();
    }

    @MCAttribute
    public void setSkipUserInfo(boolean skipUserInfo) {
        sessionAuthorizer.setSkipUserInfo(skipUserInfo);
    }

    @MCChildElement(order = 5)
    public void setPublicUrlManager(PublicUrlManager publicUrlManager) {
        this.publicUrlManager = publicUrlManager;
    }

    public PublicUrlManager getPublicUrlManager() {
        return publicUrlManager;
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
        return accessTokenRevalidator.getRevalidateTokenAfter();
    }

    /**
     * @description time in seconds until a oauth2 access token is revalidatet with authorization server. This is disabled for values &lt; 0
     * @default -1
     */
    @MCAttribute
    public void setRevalidateTokenAfter(int revalidateTokenAfter) {
        accessTokenRevalidator.setRevalidateTokenAfter(revalidateTokenAfter);
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

    public String getCustomHeaderUserPropertyPrefix() {
        return customHeaderUserPropertyPrefix;
    }

    /**
     * @description A user property prefix (e.g. "header"), which can be used to make the interceptor emit custom per-user headers.
     * For example, if you have a user property "headerX: Y" on a user U, and the user U logs in, all requests belonging to this
     * user will have an additional HTTP header "X: Y". If null, this feature is disabled.
     * @default null
     */
    @MCAttribute
    public void setCustomHeaderUserPropertyPrefix(String customHeaderUserPropertyPrefix) {
        this.customHeaderUserPropertyPrefix = customHeaderUserPropertyPrefix;
    }

    public String getLogoutUrl() {
        return logoutUrl;
    }

    /**
     * @description Path (as seen by the user agent) to call to trigger a log out.
     * If the Authorization Server supports <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">OpenID
     * Connect RP-Initiated Logout 1.0</a>, the user logout ("single log out") will be triggered there as well.
     */
    @MCAttribute
    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
    }

    public String getAfterLogoutUrl() {
        return afterLogoutUrl;
    }

    @MCAttribute
    public void setAfterLogoutUrl(String afterLogoutUrl) {
        this.afterLogoutUrl = afterLogoutUrl;
    }

    public List<LoginParameter> getLoginParameters() {
        return loginParameters;
    }

    @MCChildElement(order = 25)
    public void setLoginParameters(List<LoginParameter> loginParameters) {
        this.loginParameters = loginParameters;
    }

    public boolean isAppendAccessTokenToRequest() {
        return appendAccessTokenToRequest;
    }

    @MCAttribute
    public void setAppendAccessTokenToRequest(boolean appendAccessTokenToRequest) {
        this.appendAccessTokenToRequest = appendAccessTokenToRequest;
    }

    public String getAfterErrorUrl() {
        return afterErrorUrl;
    }

    @MCAttribute
    public void setAfterErrorUrl(String afterErrorUrl) {
        this.afterErrorUrl = afterErrorUrl;
    }

    public boolean isOnlyRefreshToken() {
        return onlyRefreshToken;
    }

    @MCAttribute
    public void setOnlyRefreshToken(boolean onlyRefreshToken) {
        this.onlyRefreshToken = onlyRefreshToken;
    }
}
