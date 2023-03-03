/*
 * Copyright 2019 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.predic8.membrane.core.interceptor.oauth2server;

import com.floreysoft.jmte.*;
import com.floreysoft.jmte.message.*;
import com.floreysoft.jmte.token.*;
import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.URI;
import com.predic8.membrane.core.util.*;
import org.apache.commons.lang3.*;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static java.nio.charset.StandardCharsets.*;

public class LoginDialog2 {
    private static final Logger log = LoggerFactory.getLogger(com.predic8.membrane.core.interceptor.authentication.session.LoginDialog.class.getName());

    private final String path;
    private final String message;
    private final boolean exposeUserCredentialsToSession;
    private URIFactory uriFactory;

    private final UserDataProvider userDataProvider;
    private final TokenProvider tokenProvider;
    private final SessionManager sessionManager;
    private final AccountBlocker accountBlocker;

    private final WebServerInterceptor wsi;

    public LoginDialog2(
            UserDataProvider userDataProvider,
            TokenProvider tokenProvider,
            SessionManager sessionManager,
            AccountBlocker accountBlocker,
            String dialogLocation,
            String path,
            boolean exposeUserCredentialsToSession,
            String message) {
        this.path = path;
        this.exposeUserCredentialsToSession = exposeUserCredentialsToSession;
        this.userDataProvider = userDataProvider;
        this.tokenProvider = tokenProvider;
        this.sessionManager = sessionManager;
        this.accountBlocker = accountBlocker;
        this.message = message;

        wsi = new WebServerInterceptor();
        wsi.setDocBase(dialogLocation);
    }

    public void init(Router router) throws Exception {
        uriFactory = router.getUriFactory();
        wsi.init(router);
        router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), wsi.getDocBase(), "index.html")).close();

    }

    public boolean isLoginRequest(Exchange exc) {
        URI uri = uriFactory.createWithoutException(exc.getRequest().getUri());
        return uri.getPath().startsWith(path);
    }

    private void showPage(Exchange exc, int page, Object... params) throws Exception {
        String target = StringUtils.defaultString(URLParamUtil.getParams(uriFactory, exc, ERROR).get("target"));

        exc.getDestinations().set(0, "/index.html");
        wsi.handleRequest(exc);

        Engine engine = new Engine();
        engine.setErrorHandler(new ErrorHandler() {

            @Override
            public void error(ErrorMessage arg0, Token arg1, Map<String, Object> arg2) throws ParseException {
                log.error(arg0.key);
            }

            @Override
            public void error(ErrorMessage arg0, Token arg1) throws ParseException {
                log.error(arg0.key);
            }
        });
        Map<Object, Object> pages = ImmutableMap
                .builder()
                .put(0, "login")
                .put(1, "token")
                .put(2, "consent")
                .build();

        Map<String, Object> model = new HashMap<>();
        model.put("action", StringEscapeUtils.escapeXml11(path)+ pages.get(page));
        model.put("target", StringEscapeUtils.escapeXml11(target));
        model.put(pages.get(page).toString(),true);

        for (int i = 0; i < params.length; i+=2)
            model.put((String)params[i], params[i+1]);

        exc.getResponse().setBodyContent(engine.transform(exc.getResponse().getBodyAsStringDecoded(), model).getBytes(UTF_8));
    }

    public void handleLoginRequest(Exchange exc) throws Exception {
        Session s = sessionManager.getSession(exc);

        String uri = exc.getRequest().getUri().substring( path.length() > 0 ? path.length()-1 : 0);
        if (uri.indexOf('?') >= 0)
            uri = uri.substring(0, uri.indexOf('?'));
        exc.getDestinations().add(uri);

        switch (uri) {
            case "/logout" -> {
                if (s != null)
                    s.clear();
                exc.setResponse(Response.redirect(path, false).body("").build());
            }
            case "/consent" -> {
                if (exc.getRequest().getMethod().equals("POST"))
                    processConsentPageResult(exc, s);
                else
                    showConsentPage(exc, s);
            }
            case "/login" -> {
                if (s == null || !s.isVerified()) {
                    if (exc.getRequest().getMethod().equals("POST")) {
                        Map<String, String> userAttributes;
                        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, ERROR);
                        String username = params.get("username");
                        if (username == null) {
                            showPage(exc, 0, "error", "INVALID_PASSWORD");
                            return;
                        }
                        if (accountBlocker != null && accountBlocker.isBlocked(username)) {
                            showPage(exc, 0, "error", "ACCOUNT_BLOCKED");
                            return;
                        }
                        try {
                            userAttributes = userDataProvider.verify(params);
                        } catch (NoSuchElementException e) {
                            List<String> params2 = Lists.newArrayList("error", "INVALID_PASSWORD");
                            if (accountBlocker != null) {
                                if (accountBlocker.fail(username))
                                    params2.addAll(Lists.newArrayList("accountBlocked", "true"));
                            }
                            showPage(exc, 0, params2.toArray());
                            return;
                        } catch (Exception e) {
                            log.error("", e);
                            showPage(exc, 0, "error", "INTERNAL_SERVER_ERROR");
                            return;
                        }
                        if (exposeUserCredentialsToSession) {
                            for (Map.Entry<String, String> param : params.entrySet())
                                if (!userAttributes.containsKey(param.getKey()))
                                    userAttributes.put(param.getKey(), param.getValue());
                        }
                        if (tokenProvider != null)
                            showPage(exc, 1);
                        else {
                            String target = params.get("target");
                            if (StringUtils.isEmpty(target))
                                target = "/";
                            exc.setResponse(Response.redirectWithout300(target).build());
                        }


                        Map<String, String> conv = s.get().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().toString(), (m1, m2) -> m1));
                        if (tokenProvider != null)
                            tokenProvider.requestToken(conv);

                        s.get().keySet().forEach(conv::remove);
                        conv.entrySet().forEach(e -> s.put(e.getKey(), e.getValue()));

                        s.authorize(username);
                    } else {
                        showPage(exc, 0);
                    }
                } else {
                    if (accountBlocker != null && accountBlocker.isBlocked(s.getUsername())) {
                        showPage(exc, 0, "error", "ACCOUNT_BLOCKED");
                        return;
                    }
                    if (exc.getRequest().getMethod().equals("POST")) {
                        String token = URLParamUtil.getParams(uriFactory, exc, ERROR).get("token");
                        try {
                            if (tokenProvider != null)
                                tokenProvider.verifyToken(s.get().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().toString(), (m1, m2) -> m1)), token);
                        } catch (NoSuchElementException e) {
                            List<String> params = Lists.newArrayList("error", "INVALID_TOKEN");
                            if (accountBlocker != null)
                                if (accountBlocker.fail(s.getUsername()))
                                    params.addAll(Lists.newArrayList("accountBlocked", "true"));
                            s.clear();
                            showPage(exc, 0, params.toArray());
                            return;
                        } catch (Exception e) {
                            log.error("", e);
                            s.clear();
                            showPage(exc, 0, "error", "INTERNAL_SERVER_ERROR");
                            return;
                        }
                        if (accountBlocker != null)
                            accountBlocker.unblock(s.getUsername());
                        String target = URLParamUtil.getParams(uriFactory, exc, ERROR).get("target");
                        if (StringUtils.isEmpty(target))
                            target = "/";

                        if (this.message != null)
                            exc.setResponse(Response.redirectWithout300(target, message).build());
                        else
                            exc.setResponse(Response.redirectWithout300(target).build());

                        s.authorize(s.getUsername());
                    } else {
                        showPage(exc, 1);
                    }
                }
            }
            default -> wsi.handleRequest(exc);
        }
    }

    private void processConsentPageResult(Exchange exc, Session s) throws Exception {
        removeConsentPageDataFromSession(s);
        putConsentInSession(exc, s);
        redirectAfterConsent(exc);
    }

    private void removeConsentPageDataFromSession(Session s) {
        if(s == null)
            return;
        synchronized (s) {
            s.remove(ConsentPageFile.PRODUCT_NAME);
            s.remove(ConsentPageFile.LOGO_URL);
            s.remove(ConsentPageFile.SCOPE_DESCRIPTIONS);
            s.remove(ConsentPageFile.CLAIM_DESCRIPTIONS);
        }
    }

    private void redirectAfterConsent(Exchange exc) throws Exception {
        String target = URLParamUtil.getParams(uriFactory, exc, ERROR).get("target");
        if (StringUtils.isEmpty(target))
            target = "/";
        exc.setResponse(Response.redirectWithout300(target).build());
    }

    private void putConsentInSession(Exchange exc, Session s) throws Exception {
        if(s == null)
            return;
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, ERROR);
        String consentResult = "false";
        if(params.get("consent").equals("Accept"))
            consentResult = "true";
        synchronized (s) {
            s.put("consent", consentResult);
        }
    }

    private void showConsentPage(Exchange exc, Session s) throws Exception {
        if(s == null){
            showPage(exc,2,ConsentPageFile.PRODUCT_NAME,null,ConsentPageFile.LOGO_URL,null,"scopes", null, "claims", null);
            return;
        }
        synchronized(s) {
            String productName = s.get(ConsentPageFile.PRODUCT_NAME);
            String logoUrl = s.get(ConsentPageFile.LOGO_URL);
            Map<String, String> scopes = doubleStringArrayToMap(prepareScopesFromSession(s));
            Map<String, String> claims = doubleStringArrayToMap(prepareClaimsFromSession(s));
            showPage(exc,2,ConsentPageFile.PRODUCT_NAME,productName,ConsentPageFile.LOGO_URL,logoUrl,"scopes", scopes, "claims", claims);
        }

    }

    private Map<String, String> doubleStringArrayToMap(String[] strings) {
        HashMap<String, String> result = new HashMap<>();
        for(String string : strings) {
            String[] str = string.split(" ");
            for(int i = 2; i < str.length;i++)
                str[1] += " " + str[i];
            result.put(str[0], str[1]);
        }
        return result;
    }

    private String[] prepareClaimsFromSession(Session s) throws UnsupportedEncodingException {
        return prepareStringArray(decodeClaimsFromSession(s));
    }

    private String[] prepareScopesFromSession(Session s) throws UnsupportedEncodingException {
        return prepareStringArray(decodeScopesFromSession(s));
    }

    private String[] prepareStringArray(String[] array){
        if(array[0].isEmpty())
            return new String[0];
        List<String> result = new ArrayList<>();
        for(int i = 0; i < array.length;i+=2)
            result.add(array[i] + ": " + array[i+1]);
        return result.toArray(new String[0]);
    }

    private String[] decodeClaimsFromSession(Session s) {
        if(s.get(ConsentPageFile.CLAIM_DESCRIPTIONS) != null) {
            String[] claims = s.<String>get(ConsentPageFile.CLAIM_DESCRIPTIONS).split(" ");
            for (int i = 0; i < claims.length; i++)
                claims[i] = OAuth2Util.urldecode(claims[i]);
            return claims;
        }
        return new String[0];
    }

    private String[] decodeScopesFromSession(Session s) {
        if(s.get(ConsentPageFile.SCOPE_DESCRIPTIONS) != null) {
            String[] scopes = s.<String>get(ConsentPageFile.SCOPE_DESCRIPTIONS).split(" ");
            for (int i = 0; i < scopes.length; i++)
                scopes[i] = OAuth2Util.urldecode(scopes[i]);
            return scopes;
        }
        return new String[0];
    }

    public Outcome redirectToLogin(Exchange exc) throws UnsupportedEncodingException {
        exc.setResponse(Response.
                redirect(path + "?target=" + URLEncoder.encode(exc.getOriginalRequestUri(), UTF_8), false).
                dontCache().
                body("").
                build());
        return RETURN;
    }

}

