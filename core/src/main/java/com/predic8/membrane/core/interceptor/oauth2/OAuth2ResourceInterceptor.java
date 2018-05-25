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
package com.predic8.membrane.core.interceptor.oauth2;

import com.floreysoft.jmte.Engine;
import com.floreysoft.jmte.ErrorHandler;
import com.floreysoft.jmte.message.ParseException;
import com.floreysoft.jmte.token.Token;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.CleanupThread;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.Util;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @description Allows only authorized HTTP requests to pass through. Unauthorized requests get a redirect to the
 * authorization server as response.
 * @topic 6. Security
 */
@MCElement(name = "oauth2Resource")
public class OAuth2ResourceInterceptor extends AbstractInterceptor {
    public static final String OAUTH2_ANSWER = "oauth2Answer";
    public static final String OA2REDIRECT = "oa2redirect";
    private static Logger log = LoggerFactory.getLogger(OAuth2ResourceInterceptor.class.getName());

    private String loginLocation;
    private String loginPath = "/login/";
    private String publicURL;
    private SessionManager sessionManager;
    private AuthorizationService auth;
    private OAuth2Statistics statistics;
    private Cache<String,Boolean> validTokens = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    private Cache<String,Exchange> stateToRedirect = CacheBuilder.newBuilder().expireAfterWrite(1,TimeUnit.MINUTES).build();

    private int revalidateTokenAfter = -1;

    private ConcurrentHashMap<String,Exchange> stateToOriginalUrl = new ConcurrentHashMap<>();

    private WebServerInterceptor wsi;
    private URIFactory uriFactory;
    private boolean firstInitWhenDynamicAuthorizationService;
    private boolean initPublicURLOnFirstExchange = false;

    public String getLoginLocation() {
        return loginLocation;
    }

    /**
     * @description location of the login dialog template (a directory containing the <i>index.html</i> file as well as possibly other resources).
     *  Required for older browsers to work.
     * @example file:c:/work/login/
     */
    @MCAttribute
    public void setLoginLocation(String login) {
        this.loginLocation = login;
    }

    public String getLoginPath() {
        return loginPath;
    }

    /**
     * @description context path of the login dialog
     * @default /login/
     */
    @MCAttribute
    public void setLoginPath(String loginPath) {
        this.loginPath = loginPath;
    }


    public String getPublicURL() {
        return publicURL;
    }

    @MCAttribute
    public void setPublicURL(String publicURL) {
        this.publicURL = publicURL;
    }

    public AuthorizationService getAuthService() {
        return auth;
    }

    @Required
    @MCChildElement(order = 10)
    public void setAuthService(AuthorizationService auth) {
        this.auth = auth;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @MCChildElement(order = 20)
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
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

    @Override
    public void init(Router router) throws Exception {
        name = "OAuth 2 Client";
        setFlow(Flow.Set.REQUEST_RESPONSE);

        super.init(router);

        auth.init(router);
        statistics = new OAuth2Statistics();
        uriFactory = router.getUriFactory();
        if (sessionManager == null)
            sessionManager = new SessionManager();
        sessionManager.setCookieName("SESSION_ID_CLIENT"); // TODO maybe do this differently as now the attribute in the bean is overwritten ( when set from external proxies.xml )
        sessionManager.init(router);

        if (loginLocation != null) {
            wsi = new WebServerInterceptor();
            wsi.setDocBase(loginLocation);
            router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), wsi.getDocBase(), "./index.html")).close();
            wsi.init(router);
        }

        if(publicURL == null)
            initPublicURLOnFirstExchange = true;
        else
            normalizePublicURL();

        firstInitWhenDynamicAuthorizationService = getAuthService().supportsDynamicRegistration();
        if(!getAuthService().supportsDynamicRegistration())
            firstInitWhenDynamicAuthorizationService = false;

        new CleanupThread(sessionManager).start();
    }

    @Override
    public final Outcome handleRequest(Exchange exc) throws Exception {
        Outcome outcome = handleRequestInternal(exc);
        if (outcome != Outcome.CONTINUE)
            sessionManager.postProcess(exc);
        return outcome;
    }

    private Outcome handleRequestInternal(Exchange exc) throws Exception {
        if(initPublicURLOnFirstExchange)
            setPublicURL(exc);

        if(firstInitWhenDynamicAuthorizationService){
            firstInitWhenDynamicAuthorizationService = false;

            getAuthService().dynamicRegistration(exc,publicURL);
        }

        if(isFaviconRequest(exc)){
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        if(isOAuth2RedirectRequest(exc))
            handleOriginalRequest(exc);

        if (isLoginRequest(exc)) {
            handleLoginRequest(exc);
            return Outcome.RETURN;
        }

        Session session = sessionManager.getSession(exc);

        if (session == null) {
            String auth = exc.getRequest().getHeader().getFirstValue(Header.AUTHORIZATION);
            if (auth != null && auth.substring(0, 7).equalsIgnoreCase("Bearer ")) {
                session = sessionManager.createSession(exc);
                session.getUserAttributes().put(ParamNames.ACCESS_TOKEN, auth.substring(7));
                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                oauth2Answer.setAccessToken(auth.substring(7));
                oauth2Answer.setTokenType("Bearer");
                HashMap<String, String> userinfo = revalidateToken(oauth2Answer);
                if (userinfo == null)
                    return respondWithRedirect(exc);
                oauth2Answer.setUserinfo(userinfo);
                session.getUserAttributes().put(OAUTH2_ANSWER,oauth2Answer.serialize());
                processUserInfo(userinfo, session);
            }
        }

        if (session == null)
            return respondWithRedirect(exc);


        if (session.getUserAttributes().get(OAUTH2_ANSWER) != null && tokenNeedsRevalidation(session.getUserAttributes().get(ParamNames.ACCESS_TOKEN))) {
            if (revalidateToken(OAuth2AnswerParameters.deserialize(session.getUserAttributes().get(OAUTH2_ANSWER))) == null)
                session.clear();
        }

        if(session.getUserAttributes().get(OAUTH2_ANSWER) != null)
            exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.getUserAttributes().get(OAUTH2_ANSWER)));

        if (refreshingOfAccessTokenIsNeeded(session)) {
            synchronized (session) {
                refreshAccessToken(session);
                exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.getUserAttributes().get(OAUTH2_ANSWER)));
            }
        }

        if (session.isAuthorized()) {
            applyBackendAuthorization(exc, session);
            statistics.successfulRequest();
            return Outcome.CONTINUE;
        }

        if (handleRequest(exc, session.getUserAttributes().get("state"), publicURL, session)) {
            if(exc.getResponse() == null && exc.getRequest() != null && session.isAuthorized() && session.getUserAttributes().containsKey(OAUTH2_ANSWER)) {
                exc.setProperty(Exchange.OAUTH2, OAuth2AnswerParameters.deserialize(session.getUserAttributes().get(OAUTH2_ANSWER)));
                return Outcome.CONTINUE;
            }
            if (exc.getResponse().getStatusCode() >= 400)
                session.clear();
            return Outcome.RETURN;
        }

        return respondWithRedirect(exc);
    }

    private void handleOriginalRequest(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);
        String oa2redirect = params.get(OA2REDIRECT);

        Exchange originalExchange = null;
        synchronized (stateToRedirect) {
            originalExchange = stateToRedirect.getIfPresent(oa2redirect);
            stateToRedirect.invalidate(oa2redirect);
        }

        doOriginalRequest(exc,originalExchange);
    }

    private boolean isOAuth2RedirectRequest(Exchange exc) {
        return exc.getOriginalRequestUri().contains(OA2REDIRECT);
    }

    private void refreshAccessToken(Session session) throws Exception {

        if(!refreshingOfAccessTokenIsNeeded(session))
            return;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.getUserAttributes().get(OAUTH2_ANSWER));
        Exchange refreshTokenExchange = new Request.Builder()
                .post(auth.getTokenEndpoint())
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(Header.ACCEPT, "application/json")
                .header(Header.USER_AGENT, Constants.USERAGENT)
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
        oauth2Params.setReceivedAt(LocalDateTime.now());
        if (json.containsKey("id_token")) {
            if (idTokenIsValid(json.get("id_token")))
                oauth2Params.setIdToken(json.get("id_token"));
            else
                oauth2Params.setIdToken("INVALID");
        }

        session.getUserAttributes().put(OAUTH2_ANSWER, oauth2Params.serialize());

    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session) throws IOException {
        if(session.getUserAttributes().get(OAUTH2_ANSWER) == null)
            return false;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.getUserAttributes().get(OAUTH2_ANSWER));
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
    public Outcome handleResponse(Exchange exc) throws Exception {
        sessionManager.postProcess(exc);
        return super.handleResponse(exc);
    }

    private void setPublicURL(Exchange exc) {
        String xForwardedProto = exc.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_PROTO);
        boolean isHTTPS = xForwardedProto != null ? "https".equals(xForwardedProto) : exc.getRule().getSslInboundContext() != null;
        publicURL = (isHTTPS ? "https://" : "http://") + exc.getOriginalHostHeader();
        RuleKey key = exc.getRule().getKey();
        if (!key.isPathRegExp() && key.getPath() != null)
            publicURL += key.getPath();
        normalizePublicURL();
        initPublicURLOnFirstExchange = false;
    }

    private void normalizePublicURL() {
        if(!publicURL.endsWith("/"))
            publicURL += "/";
    }

    private boolean isFaviconRequest(Exchange exc) {
        return exc.getRequestURI().startsWith("/favicon.ico");
    }

    private void applyBackendAuthorization(Exchange exc, Session s) {
        Header h = exc.getRequest().getHeader();
        for (Map.Entry<String, String> e : s.getUserAttributes().entrySet())
            if (e.getKey().startsWith("header")) {
                String headerName = e.getKey().substring(6);
                h.removeFields(headerName);
                h.add(headerName, e.getValue());
            }

    }

    private Outcome respondWithRedirect(Exchange exc) {
        if (loginLocation == null) {
            String state = new BigInteger(130, new SecureRandom()).toString(32);

            exc.setResponse(Response.redirect(auth.getLoginURL(state, publicURL, exc.getRequestURI()),false).build());

            stateToOriginalUrl.put(state,exc);

            Session session = sessionManager.getOrCreateSession(exc);
            synchronized(session){
                if(session.getUserAttributes().containsKey(ParamNames.STATE))
                    state = session.getUserAttributes().get(ParamNames.STATE) + " " + state;
                if(!session.isPreAuthorized() || !session.isAuthorized())
                    session.preAuthorize("",new HashMap<>());
                session.getUserAttributes().put(ParamNames.STATE,state);
            }
        } else {
            exc.setResponse(Response.redirect(loginPath,false).build());
        }
        return Outcome.RETURN;
    }


    public boolean isLoginRequest(Exchange exc) {
        URI uri = router.getUriFactory().createWithoutException(exc.getRequest().getUri());
        return uri.getPath().startsWith(loginPath);
    }

    private void showPage(Exchange exc, String state, Object... params) throws Exception {
        String target = StringUtils.defaultString(URLParamUtil.getParams(router.getUriFactory(), exc).get("target"));

        exc.getDestinations().set(0, "/index.html");
        wsi.handleRequest(exc);

        Engine engine = new Engine();
        engine.setErrorHandler(new ErrorHandler() {

            @Override
            public void error(String arg0, Token arg1, Map<String, Object> arg2) throws ParseException {
                log.error(arg0);
            }

            @Override
            public void error(String arg0, Token arg1) throws ParseException {
                log.error(arg0);
            }
        });
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("loginPath", StringEscapeUtils.escapeXml(loginPath));
        String pathQuery = "/"; // TODO: save original request and restore it when authorized
        String url = auth.getLoginURL(state, publicURL, pathQuery);
        model.put("loginURL", url);
        model.put("target", StringEscapeUtils.escapeXml(target));
        model.put("authid", state);
        for (int i = 0; i < params.length; i += 2)
            model.put((String) params[i], params[i + 1]);

        exc.getResponse().setBodyContent(engine.transform(exc.getResponse().getBodyAsStringDecoded(), model).getBytes(Constants.UTF_8_CHARSET));
    }

    public void handleLoginRequest(Exchange exc) throws Exception {
        Session s = sessionManager.getSession(exc);

        String uri = exc.getRequest().getUri().substring(loginPath.length() - 1);
        if (uri.indexOf('?') >= 0)
            uri = uri.substring(0, uri.indexOf('?'));
        exc.getDestinations().set(0, uri);

        if (uri.equals("/logout")) {
            if (s != null && s.getUserAttributes() != null) {
                String token;
                synchronized (s) {
                    token = s.getUserAttributes().get("access_token");
                }
                Exchange e = new Request.Builder().post(auth.getRevocationEndpoint())
                        .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                        .header(Header.USER_AGENT, Constants.USERAGENT)
                        .body("token=" + token) // TODO maybe send client credentials ( as it was before ) but Google doesn't accept that
                        .buildExchange();
                Response response = auth.doRequest(e);
                if (response.getStatusCode() != 200)
                    throw new RuntimeException("Revocation of token did not work. Statuscode: " + response.getStatusCode() + ".");
                s.clear();
                sessionManager.removeSession(exc);
            }
            exc.setResponse(Response.redirect("/", false).build());
        } else if (uri.equals("/")) {
            if (s == null || !s.isAuthorized()) {
                String state = new BigInteger(130, new SecureRandom()).toString(32);
                showPage(exc, state);

                Session session = sessionManager.createSession(exc);

                HashMap<String, String> userAttributes = new HashMap<String, String>();
                userAttributes.put("state", state);
                session.preAuthorize("", userAttributes);
            } else {
                showPage(exc, s.getUserAttributes().get("state"));
            }
        } else {
            wsi.handleRequest(exc);
        }
    }

    public boolean handleRequest(Exchange exc, String state, String publicURL, Session session) throws Exception {
        String path = uriFactory.create(exc.getDestinations().get(0)).getPath();

        if(path == null)
            return false;


        if(path.endsWith("/oauth2callback")) {

            try {
                Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);

                String state2 = params.get("state");

                if (state2 == null)
                    throw new RuntimeException("No CSRF token.");

                Map<String, String> param = URLParamUtil.parseQueryString(state2);

                if (param == null || !param.containsKey("security_token"))
                    throw new RuntimeException("No CSRF token.");

                boolean csrfMatch = false;

                for(String state3 : stateToOriginalUrl.keySet())
                    if (param.get("security_token").equals(state3))
                        csrfMatch = true;

                if(!csrfMatch)
                    throw new RuntimeException("CSRF token mismatch.");


                Exchange originalRequest = stateToOriginalUrl.get(param.get("security_token"));
                String url = originalRequest.getRequest().getUri();
                if (url == null)
                    url = "/";
                stateToOriginalUrl.remove(state2);

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
                        .body("code=" + code
                                + "&client_id=" + auth.getClientId()
                                + "&client_secret=" + auth.getClientSecret()
                                + "&redirect_uri=" + publicURL + "oauth2callback"
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
                    throw new RuntimeException("Authentication server returned " + response.getStatusCode() + ".");
                }

                if (log.isDebugEnabled())
                    logi.handleResponse(e);

                HashMap<String, String> json = Util.parseSimpleJSONResponse(response);

                if (!json.containsKey("access_token"))
                    throw new RuntimeException("No access_token received.");

                String token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"

                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();

                synchronized (session){
                    session.getUserAttributes().put("access_token",token); // saving for logout
                }

                oauth2Answer.setAccessToken(token);
                oauth2Answer.setTokenType(json.get("token_type"));
                oauth2Answer.setExpiration(json.get("expires_in"));
                oauth2Answer.setRefreshToken(json.get("refresh_token"));
                oauth2Answer.setReceivedAt(LocalDateTime.now());
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

                session.getUserAttributes().put(OAUTH2_ANSWER,oauth2Answer.serialize());

                processUserInfo(json2, session);

                doRedirect(exc,originalRequest);
                return true;
            } catch (Exception e) {
                exc.setResponse(Response.badRequest().body(e.getMessage()).build());
                return true;
            }
        }
        return false;
    }

    private void doRedirect(Exchange exc, Exchange originalRequest) {
        if(exc.getRequest().getMethod().equals("GET")){
            exc.setResponse(Response.redirect(originalRequest.getRequestURI(),false).build());
        }else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);
            synchronized (stateToRedirect) {
                stateToRedirect.put(oa2redirect, originalRequest);
            }
            String delimiter = originalRequest.getRequestURI().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getRequestURI() + delimiter + OA2REDIRECT + "=" + oa2redirect, false).build());
        }
    }

    private void doOriginalRequest(Exchange exc, Exchange originalRequest) throws Exception {
        originalRequest.getRequest().getHeader().add("Cookie",exc.getRequest().getHeader().getFirstValue("Cookie"));
        originalRequest.getDestinations().clear();
        String xForwardedProto = originalRequest.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_PROTO);
        String xForwardedHost = originalRequest.getRequest().getHeader().getFirstValue(Header.X_FORWARDED_HOST);
        String originalRequestUri = originalRequest.getOriginalRequestUri();
        originalRequest.getDestinations().add(xForwardedProto + "://" + xForwardedHost + originalRequestUri);
    }

    private void processUserInfo(Map<String, String> userInfo, Session session) {
        if (!userInfo.containsKey(auth.getSubject()))
            throw new RuntimeException("User object does not contain " + auth.getSubject() + " key.");

        Map<String, String> userAttributes = session.getUserAttributes();
        String userIdPropertyFixed = auth.getSubject().substring(0, 1).toUpperCase() + auth.getSubject().substring(1);
        synchronized (userAttributes) {
            userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, userInfo.get(auth.getSubject()));
        }

        session.authorize();
    }

    private boolean idTokenIsValid(String idToken) throws Exception {
        //TODO maybe change this to return claims and also save them in the oauth2AnswerParameters
        try {
            JwtGenerator.getClaimsFromSignedIdToken(idToken, getAuthService().getIssuer(), getAuthService().getClientId(), getAuthService().getJwksEndpoint(),auth.getHttpClient());
            return true;
        }catch(Exception e){
            return false;
        }
    }

    @Override
    public String getShortDescription() {
        return "Client of the oauth2 authentication process.\n" + statistics.toString();
    }

}
