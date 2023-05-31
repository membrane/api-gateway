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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.cache.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.jwt.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.*;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.*;
import com.predic8.membrane.core.interceptor.session.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import jakarta.mail.internet.*;
import org.apache.commons.codec.binary.Base64;
import org.jose4j.jws.*;
import org.jose4j.jwt.*;
import org.jose4j.lang.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;
import java.io.*;
import java.math.*;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.session.SessionManager.*;

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
    private static final Logger log = LoggerFactory.getLogger(OAuth2Resource2Interceptor.class.getName());

    private final Cache<String, Object> synchronizers = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build();

    @GuardedBy("publicURLs")
    private final List<String> publicURLs = new ArrayList<>();
    private AuthorizationService auth;
    private OAuth2Statistics statistics;
    private final Cache<String,Boolean> validTokens = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    private int revalidateTokenAfter = -1;

    private URIFactory uriFactory;
    private boolean firstInitWhenDynamicAuthorizationService;
    private boolean initPublicURLsOnTheFly = false;
    private OriginalExchangeStore originalExchangeStore;
    private String callbackPath = "oauth2callback";

    private final ObjectMapper om = new ObjectMapper();
    private Key key;
    private X509Certificate certificate;
    private boolean skipUserInfo;
    private JwtAuthInterceptor jwtAuthInterceptor;

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
            Collections.addAll(publicURLs, publicURL.split("[ \t]+"));
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
            else publicURLs.replaceAll(this::normalizePublicURL);
        }

        firstInitWhenDynamicAuthorizationService = getAuthService().supportsDynamicRegistration();
        if(!getAuthService().supportsDynamicRegistration())
            firstInitWhenDynamicAuthorizationService = false;

        if (auth.isUseJWTForClientAuth()) {
            Object pemObject = PEMSupport.getInstance().parseKey(auth.getSslParser().getKey().getPrivate().get(router.getResolverMap(), router.getBaseLocation()));
            key = pemObject instanceof Key ? (Key) pemObject : ((KeyPair)pemObject).getPrivate();
            certificate = PEMSupport.getInstance().parseCertificate(auth.getSslParser().getKey().getCertificates().get(0).get(router.getResolverMap(), router.getBaseLocation()));
        }

        if (skipUserInfo) {
            jwtAuthInterceptor = new JwtAuthInterceptor();
            Jwks jwks = new Jwks();
            jwks.setJwks(new ArrayList<>());
            jwks.setJwksUris(auth.getJwksEndpoint());
            jwtAuthInterceptor.setJwks(jwks);
            jwtAuthInterceptor.setExpectedAud("any!!");
            jwtAuthInterceptor.init(router);
        }
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

        if (!skipUserInfo && (session == null || !session.isVerified())) {
            String auth = exc.getRequest().getHeader().getFirstValue(AUTHORIZATION);
            if (auth != null && auth.substring(0, 7).equalsIgnoreCase("Bearer ")) {
                session = getSessionManager().getSession(exc);
                session.put(ParamNames.ACCESS_TOKEN, auth.substring(7));
                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                oauth2Answer.setAccessToken(auth.substring(7));
                oauth2Answer.setTokenType("Bearer");
                Map<String, Object> userinfo = revalidateToken(oauth2Answer);
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
                    case '\"' -> inString = false;
                    case '\\' -> escapeNext = true;
                }
            } else {
                switch (c) {
                    case '{' -> curlyBraceLevel++;
                    case '}' -> curlyBraceLevel--;
                    case ',' -> {
                        if (curlyBraceLevel == 0)
                            return i;
                    }
                    case '"' -> inString = true;
                }
            }
        }
        return -1;
    }

    private Object getTokenSynchronizer(Session session) {
        OAuth2AnswerParameters oauth2Params;
        try {
            oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String rt = oauth2Params.getRefreshToken();
        if (rt == null)
            return new Object();

        try {
            return synchronizers.get(rt, Object::new);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleOriginalRequest(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
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
        Exchange refreshTokenExchange = applyAuth(auth, new Request.Builder()
                .post(auth.getTokenEndpoint())
                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .header(ACCEPT, APPLICATION_JSON)
                .header(USER_AGENT, USERAGENT),
                "grant_type=refresh_token"
                        + "&refresh_token=" + oauth2Params.getRefreshToken())
                .buildExchange();

        Response refreshTokenResponse = auth.doRequest(refreshTokenExchange);
        if (!refreshTokenResponse.isOk()) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode from authorization server for refresh token request: " + refreshTokenResponse.getStatusCode());
        }
        if (!isJson(refreshTokenResponse))
            throw new RuntimeException("Refresh Token response is no JSON.");

        @SuppressWarnings("unchecked")
        Map<String, Object> json = om.readValue(refreshTokenResponse.getBodyAsStreamDecoded(), Map.class);

        if (json.get("access_token") == null || json.get("refresh_token") == null) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + refreshTokenResponse.getStatusCode());
        }
        oauth2Params.setAccessToken((String)json.get("access_token"));
        oauth2Params.setRefreshToken((String)json.get("refresh_token"));
        oauth2Params.setExpiration(numberToString(json.get("expires_in")));
        LocalDateTime now = LocalDateTime.now();
        oauth2Params.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (idTokenIsValid((String)json.get("id_token")))
                oauth2Params.setIdToken((String)json.get("id_token"));
            else
                oauth2Params.setIdToken("INVALID");
        }

        session.put(OAUTH2_ANSWER, oauth2Params.serialize());

    }

    private Request.Builder applyAuth(AuthorizationService auth, Request.Builder requestBuilder, String body) {

        if (auth.isUseJWTForClientAuth()) {
            body += "&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" +
                    "&client_assertion=" + createClientToken(auth);
        }

        String clientSecret = auth.getClientSecret();
        if (clientSecret != null)
            requestBuilder
                    .header(AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((auth.getClientId() + ":" + clientSecret).getBytes())))
                    .body(body);
        else
            requestBuilder.body(body + "&client_id" + auth.getClientId());
        return requestBuilder;
    }

    private String createClientToken(AuthorizationService auth) {
        try {
            String jwtSub = auth.getClientId();
            String jwtAud = auth.getTokenEndpoint();

            // see https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-certificate-credentials
            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setSubject(jwtSub);
            jwtClaims.setAudience(jwtAud);
            jwtClaims.setIssuer(jwtClaims.getSubject());
            jwtClaims.setJwtId(UUID.randomUUID().toString());
            jwtClaims.setIssuedAtToNow();
            NumericDate expiration = NumericDate.now();
            expiration.addSeconds(300);
            jwtClaims.setExpirationTime(expiration);
            jwtClaims.setNotBeforeMinutesInThePast(2f);

            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(jwtClaims.toJson());
            jws.setKey(key);
            jws.setX509CertSha1ThumbprintHeaderValue(certificate);
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jws.setHeader("typ", "JWT");

            return jws.getCompactSerialization();
        } catch (JoseException | MalformedClaimException e) {
            throw new RuntimeException(e);
        }
    }

    private String numberToString(Object number) {
        if (number == null)
            return null;
        if (number instanceof Integer in)
            return in.toString();
        if (number instanceof Long ln)
            return ln.toString();
        if (number instanceof Double)
            return number.toString();
        if (number instanceof String s)
            return s;
        log.warn("Unhandled number type " + number.getClass().getName());
        return null;
    }

    private boolean isJson(Response g) throws ParseException {
        String contentType = g.getHeader().getFirstValue("Content-Type");
        if (contentType == null)
            return false;
        return g.getHeader().getContentTypeObject().match(APPLICATION_JSON);
    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session) throws IOException {
        if(session.get(OAUTH2_ANSWER) == null)
            return false;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        if(oauth2Params.getRefreshToken() == null || oauth2Params.getRefreshToken().isEmpty() || oauth2Params.getExpiration() == null || oauth2Params.getExpiration().isEmpty())
            return false;

        return LocalDateTime.now().isAfter(oauth2Params.getReceivedAt().plusSeconds(Long.parseLong(oauth2Params.getExpiration())).minusSeconds(5)); // refresh token 5 seconds before expiration
    }

    private Map<String, Object> revalidateToken(OAuth2AnswerParameters params) throws Exception {

        Exchange e2 = new Request.Builder()
                .get(auth.getUserInfoEndpoint())
                .header("Authorization", params.getTokenType() + " " + params.getAccessToken())
                .header("User-Agent", USERAGENT)
                .header(ACCEPT, APPLICATION_JSON)
                .buildExchange();

        Response response2 = auth.doRequest(e2);


        if (response2.getStatusCode() != 200) {
            statistics.accessTokenInvalid();
            return null;
        } else {
            statistics.accessTokenValid();
            if (!isJson(response2))
                throw new RuntimeException("Response is no JSON.");

            //noinspection unchecked
            return om.readValue(response2.getBodyAsStreamDecoded(), Map.class);
        }
    }

    private boolean tokenNeedsRevalidation(String token) {
        if(revalidateTokenAfter < 0)
            return false;
        return validTokens.getIfPresent(token) == null;

    }

    @Override
    protected Outcome handleResponseInternal(Exchange exc) {
        return Outcome.CONTINUE;
    }

    private String getPublicURL(Exchange exc) throws Exception {
        String xForwardedProto = exc.getRequest().getHeader().getFirstValue(X_FORWARDED_PROTO);
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
            state = session.get(ParamNames.STATE) + SESSION_VALUE_SEPARATOR + state;
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
                Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

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

                Exchange e = applyAuth(auth, new Request.Builder()
                        .post(auth.getTokenEndpoint())
                        .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                        .header(ACCEPT, APPLICATION_JSON)
                        .header(USER_AGENT, USERAGENT),
                        "code=" + code
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

                if (!isJson(response))
                    throw new RuntimeException("Token response is no JSON.");

                @SuppressWarnings("unchecked")
                Map<String, Object> json = om.readValue(response.getBodyAsStreamDecoded(), Map.class);

                if (!json.containsKey("access_token"))
                    throw new RuntimeException("No access_token received.");

                String token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"

                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();

                session.put("access_token",token); // saving for logout

                oauth2Answer.setAccessToken(token);
                oauth2Answer.setTokenType((String)json.get("token_type"));
                oauth2Answer.setExpiration(numberToString(json.get("expires_in")));
                oauth2Answer.setRefreshToken((String)json.get("refresh_token"));
                LocalDateTime now = LocalDateTime.now();
                oauth2Answer.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
                if(json.containsKey("id_token")) {
                    if (idTokenIsValid((String)json.get("id_token")))
                        oauth2Answer.setIdToken((String)json.get("id_token"));
                    else
                        oauth2Answer.setIdToken("INVALID");
                }

                validTokens.put(token,true);

                if (!skipUserInfo) {
                    Exchange e2 = new Request.Builder()
                            .get(auth.getUserInfoEndpoint())
                            .header("Authorization", json.get("token_type") + " " + token)
                            .header("User-Agent", USERAGENT)
                            .header(ACCEPT, APPLICATION_JSON)
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

                    if (!isJson(response2))
                        throw new RuntimeException("Userinfo response is no JSON.");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> json2 = om.readValue(response2.getBodyAsStreamDecoded(), Map.class);

                    oauth2Answer.setUserinfo(json2);

                    session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

                    processUserInfo(json2, session);
                } else {
                    session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

                    // assume access token is JWT
                    if (jwtAuthInterceptor.handleJwt(exc, token) != Outcome.CONTINUE)
                        throw new RuntimeException("Access token is not a JWT.");

                    processUserInfo((Map<String, Object>) exc.getProperty("jwt"), session);
                }

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

        Map<String, String> param = URLParamUtil.parseQueryString(state2, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

        if (param == null || !param.containsKey("security_token"))
            throw new RuntimeException("No CSRF token.");

        return param.get("security_token");
    }

    private boolean csrfTokenMatches(Session session, String state2) {
        Optional<Object> sessionRaw = Optional.ofNullable(session.get(ParamNames.STATE));
        if(sessionRaw.isEmpty())
            return false;
        return Arrays.stream(sessionRaw.get().toString().split(SESSION_VALUE_SEPARATOR))
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

    private void doOriginalRequest(Exchange exc, AbstractExchange originalRequest) {
        originalRequest.getRequest().getHeader().add("Cookie",exc.getRequest().getHeader().getFirstValue("Cookie"));
        exc.setRequest(originalRequest.getRequest());

        exc.getDestinations().clear();
        String xForwardedProto = originalRequest.getRequest().getHeader().getFirstValue(X_FORWARDED_PROTO);
        String xForwardedHost = originalRequest.getRequest().getHeader().getFirstValue(X_FORWARDED_HOST);
        String originalRequestUri = originalRequest.getOriginalRequestUri();
        exc.getDestinations().add(xForwardedProto + "://" + xForwardedHost + originalRequestUri);

        exc.setOriginalRequestUri(originalRequestUri);
        exc.setOriginalHostHeader(xForwardedHost);
    }

    private void processUserInfo(Map<String, Object> userInfo, Session session) {
        if (!userInfo.containsKey(auth.getSubject()))
            throw new RuntimeException("User object does not contain " + auth.getSubject() + " key.");

        Map<String, Object> userAttributes = session.get();
        String userIdPropertyFixed = auth.getSubject().substring(0, 1).toUpperCase() + auth.getSubject().substring(1);
        String username = (String) userInfo.get(auth.getSubject());
        userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, username);

        session.authorize(username);
    }

    private boolean idTokenIsValid(String idToken) {
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

    public boolean isSkipUserInfo() {
        return skipUserInfo;
    }

    @MCAttribute
    public void setSkipUserInfo(boolean skipUserInfo) {
        this.skipUserInfo = skipUserInfo;
    }
}
