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
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptorWithSession;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.*;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRefresher;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRevalidator;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;

import static com.predic8.membrane.core.http.Header.X_FORWARDED_HOST;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_PROTO;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuthUtilsStuff.isOAuth2RedirectRequest;
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

    private AuthorizationService auth;
    private OAuth2Statistics statistics;
    private URIFactory uriFactory;
    private OriginalExchangeStore originalExchangeStore;
    private String callbackPath = "oauth2callback";

    private final AccessTokenRevalidator accessTokenRevalidator = new AccessTokenRevalidator();
    private final AccessTokenRefresher accessTokenRefresher = new AccessTokenRefresher();
    private PublicUrlStuff publicUrlStuff = new PublicUrlStuff();
    private final UserInfoHandler userInfoHandler = new UserInfoHandler();

    @Override
    public void init() throws Exception {
        super.init();

        if (originalExchangeStore == null) {
            originalExchangeStore = new CookieOriginialExchangeStore();
        }
    }

    @Override
    public void init(Router router) throws Exception {
        name = "OAuth 2 Client";
        setFlow(Flow.Set.REQUEST_RESPONSE);

        super.init(router);

        auth.init(router);
        statistics = new OAuth2Statistics();
        uriFactory = router.getUriFactory();

        publicUrlManager.init(auth, callbackPath);
        accessTokenRevalidator.init(auth, statistics);
        accessTokenRefresher.init(auth);
        userInfoHandler.init(auth, router);
    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) {
        return Outcome.CONTINUE;
    }

    @Override
    public final Outcome handleRequestInternal(Exchange exc) throws Exception {
        if (isFaviconRequest(exc)) {
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        Session session = getSessionManager().getSession(exc);
        OAuthUtilsStuff.simplifyMultipleOAuth2Answers(session);

        if (isOAuth2RedirectRequest(exc)) {
            handleOriginalRequest(exc);
        }

        // TODO: eigene klasse, soll austauschbar sein (MCchild element)
        if (KommtSchonMitJWTFall.userInfoIsNullAndShouldRedirect(userInfoHandler, session, exc, statistics, accessTokenRevalidator, auth)) {
            return respondWithRedirect(exc);
        }

        accessTokenRevalidator.revalidateIfNeeded(session);

        if (session.hasOAuth2Answer()) {
            exc.setProperty(Exchange.OAUTH2, session.getOAuth2AnswerParameters());
        }

        accessTokenRefresher.refreshIfNeeded(session, exc);

        if (session.isVerified()) {
            applyBackendAuthorization(exc, session);
            statistics.successfulRequest();
            return Outcome.CONTINUE;
        }

        if (handleRequest(exc, publicUrlStuff.getPublicURL(exc, getAuthService(), callbackPath), session)) {
            if (exc.getResponse() == null && exc.getRequest() != null && session.isVerified() && session.hasOAuth2Answer()) {
                exc.setProperty(Exchange.OAUTH2, session.getOAuth2AnswerParameters());
                return Outcome.CONTINUE;
            }

            if (exc.getResponse().getStatusCode() >= 400) {
                session.clear();
            }

            return Outcome.RETURN;
        }

        log.debug("session present, but not verified, redirecting.");
        return respondWithRedirect(exc);
    }

    private void handleOriginalRequest(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
        String oa2redirect = params.get(OA2REDIRECT);

        Session session = getSessionManager().getSession(exc);

        AbstractExchange originalExchange = new ObjectMapper().readValue(
                        session.get(OAuthUtilsStuff.oa2redictKeyNameInSession(oa2redirect)).toString(),
                        AbstractExchangeSnapshot.class)
                .toAbstractExchange();
        session.remove(OAuthUtilsStuff.oa2redictKeyNameInSession(oa2redirect));

        doOriginalRequest(exc, originalExchange);
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

        exc.setResponse(Response.redirect(auth.getLoginURL(state, publicUrlManager.getPublicURL(exc), exc.getRequestURI()), false).build());

        readBodyFromStreamIntoMemory(exc);

        Session session = getSessionManager().getSession(exc);

        originalExchangeStore.store(exc, session, state, exc);

        if (session.get().containsKey(ParamNames.STATE))
            state = session.get(ParamNames.STATE) + SESSION_VALUE_SEPARATOR + state;
        session.put(ParamNames.STATE, state);

        return Outcome.RETURN;
    }

    private void readBodyFromStreamIntoMemory(Exchange exc) {
        exc.getRequest().getBodyAsStringDecoded();
    }

    private boolean handleRequest(Exchange exc, String publicURL, Session session) throws Exception {
        String path = uriFactory.create(exc.getDestinations().getFirst()).getPath();

        if (path == null) {
            return false;
        }

        if (path.endsWith("/" + callbackPath)) {
            return OAuth2CallbackRequestHandler.machMal(
                    uriFactory, exc, session, auth, originalExchangeStore, publicURL, callbackPath,
                    accessTokenRevalidator, statistics, userInfoHandler
            );
        }

        return false;
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
        return userInfoHandler.isSkip();
    }

    @MCAttribute
    public void setSkipUserInfo(boolean skipUserInfo) {
        userInfoHandler.setSkip(skipUserInfo);
    }

    @MCChildElement
    public void setPublicUrlStuff(PublicUrlStuff publicUrlStuff) {
        this.publicUrlStuff = publicUrlStuff;
    }

    public PublicUrlStuff getPublicUrlStuff() {
        return publicUrlStuff;
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
}
