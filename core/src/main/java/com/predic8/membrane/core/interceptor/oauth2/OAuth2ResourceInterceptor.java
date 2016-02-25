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
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.Util;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * @description Allows only authorized HTTP requests to pass through. Unauthorized requests get a redirect to the
 * authorization server as response.
 * @topic 6. Security
 */
@MCElement(name = "oauth2Resource")
public class OAuth2ResourceInterceptor extends AbstractInterceptor {
    private static Log log = LogFactory.getLog(OAuth2ResourceInterceptor.class.getName());

    private String loginLocation, loginPath = "/login/", publicURL;
    private SessionManager sessionManager;
    private AuthorizationService auth;

    private WebServerInterceptor wsi;
    private URIFactory uriFactory;

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

    @Required
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

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        auth.init(router);
        uriFactory = router.getUriFactory();
        if (sessionManager == null)
            sessionManager = new SessionManager();
        sessionManager.init(router);

        if (loginLocation != null) {
            wsi = new WebServerInterceptor();
            wsi.setDocBase(loginLocation);
            router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), wsi.getDocBase(), "./index.html")).close();
            wsi.init(router);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        if(isFaviconRequest(exc)){
            exc.setResponse(Response.badRequest().build());
            return Outcome.RETURN;
        }

        if (isLoginRequest(exc)) {
            handleLoginRequest(exc);
            return Outcome.RETURN;
        }

        Session session = sessionManager.getSession(exc.getRequest());
        if(session != null && session.getUserAttributes() == null)
            session = null; // session was logged out

        if (session == null)
            return respondWithRedirect(exc);

        if (session.isAuthorized()) {
            applyBackendAuthorization(exc, session);
            return Outcome.CONTINUE;
        }

        if (handleRequest(exc, session.getUserAttributes().get("state"), publicURL, session)) {
            if (exc.getResponse().getStatusCode() >= 400)
                session.clear();
            return Outcome.RETURN;
        }


        return respondWithRedirect(exc);
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

            exc.setResponse(Response.redirect(auth.getLoginURL(state, publicURL, exc.getRequestURI()), false).build());

            Session session = sessionManager.createSession(exc);

            HashMap<String, String> userAttributes = new HashMap<String, String>();
            userAttributes.put("state", state);
            session.preAuthorize("", userAttributes);
        } else {
            exc.setResponse(Response.redirect(loginPath, false).build());
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
        Session s = sessionManager.getSession(exc.getRequest());

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
                        .body("token=" + token +"&client_id=" + auth.getClientId() + "&client_secret=" + auth.getClientSecret())
                        .buildExchange();
                Response response = auth.httpClient.call(e).getResponse();
                if (response.getStatusCode() != 200) {
                    response.getBody().read();
                    throw new RuntimeException("Revocation of token did not work. Statuscode: " + response.getStatusCode() + ".");
                }
                s.clear();
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

        if ("/oauth2callback".equals(path)) {

            try {
                Map<String, String> params = URLParamUtil.getParams(uriFactory, exc);

                String state2 = params.get("state");

                if (state2 == null)
                    throw new RuntimeException("No CSRF token.");

                Map<String, String> param = URLParamUtil.parseQueryString(state2);

                if (param == null || !param.containsKey("security_token"))
                    throw new RuntimeException("No CSRF token.");

                if (!param.get("security_token").equals(state))
                    throw new RuntimeException("CSRF token mismatch.");

                String url = param.get("url");
                if (url == null)
                    url = "/";

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

                Response response = auth.httpClient.call(e).getResponse();

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

                synchronized (session){
                    session.getUserAttributes().put("access_token",token); // saving for logout
                }

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

                Response response2 = auth.httpClient.call(e2).getResponse();

                if (log.isDebugEnabled())
                    logi.handleResponse(e2);

                if (response2.getStatusCode() != 200) {
                    throw new RuntimeException("User data could not be retrieved.");
                }

                HashMap<String, String> json2 = Util.parseSimpleJSONResponse(response2);

                if (!json2.containsKey(auth.getUserIDProperty()))
                    throw new RuntimeException("User object does not contain " + auth.getUserIDProperty() + " key.");

                Map<String, String> userAttributes = session.getUserAttributes();
                String userIdPropertyFixed = auth.getUserIDProperty().substring(0, 1).toUpperCase() + auth.getUserIDProperty().substring(1);
                synchronized (userAttributes) {
                    userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, json2.get(auth.getUserIDProperty()));
                }

                session.authorize();

                exc.setResponse(Response.redirect(url, false).build());
                return true;
            } catch (Exception e) {
                exc.setResponse(Response.badRequest().body(e.getMessage()).build());
            }
        }
        return false;
    }
}
