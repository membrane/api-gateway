package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.OriginalExchangeStore;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRevalidator;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.csrfTokenMatches;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.getSecurityTokenFromState;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.*;

public class OAuth2CallbackRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2CallbackRequestHandler.class);

    LogHelper logHelper = new LogHelper();
    private URIFactory uriFactory;
    private AuthorizationService auth;
    private OriginalExchangeStore originalExchangeStore;
    private AccessTokenRevalidator accessTokenRevalidator;
    private SessionAuthorizer sessionAuthorizer;
    private PublicUrlManager publicUrlManager;
    private String callbackPath;

    public void init(
            URIFactory uriFactory,
            AuthorizationService auth,
            OriginalExchangeStore originalExchangeStore,
            AccessTokenRevalidator accessTokenRevalidator,
            SessionAuthorizer sessionAuthorizer,
            PublicUrlManager publicUrlManager,
            String callbackPath
            ) {
        this.uriFactory = uriFactory;
        this.auth = auth;
        this.originalExchangeStore = originalExchangeStore;
        this.accessTokenRevalidator = accessTokenRevalidator;
        this.sessionAuthorizer = sessionAuthorizer;
        this.publicUrlManager = publicUrlManager;
        this.callbackPath = callbackPath;
    }

    public boolean handleRequest(Exchange exc, Session session) throws Exception {
        try {
            Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

            String state2 = params.get("state");

            String stateFromUri = getSecurityTokenFromState(state2);

            if (!csrfTokenMatches(session, stateFromUri)) {
                throw new RuntimeException("CSRF token mismatch.");
            }

            // state in session can be "merged" -> save the selected state in session overwriting the possibly merged value
            session.put(ParamNames.STATE, stateFromUri);

            AbstractExchangeSnapshot originalRequest = originalExchangeStore.reconstruct(exc, session, stateFromUri);
            String url = originalRequest.getRequest().getUri();
            if (url == null) {
                url = "/";
            }
            originalExchangeStore.remove(exc, session, stateFromUri);

            if (log.isDebugEnabled()) {
                log.debug("CSRF token match.");
            }

            Map<String, Object> json = exchangeCodeForToken(
                    publicUrlManager.getPublicURL(exc), params);

            if (!json.containsKey("access_token")) {
                throw new RuntimeException("No access_token received.");
            }

            String token = (String) json.get("access_token"); // and also "scope": "", "token_type": "bearer"

            session.put("access_token", token); // saving for logout

            OAuth2AnswerParameters oauth2Answer = initOAuth2AnswerParameters(json, token);

            accessTokenRevalidator.getValidTokens().put(token, true);

            if (!sessionAuthorizer.isSkipUserInfo()) {
                sessionAuthorizer.retrieveUserInfo(json.get("token_type").toString(), token, oauth2Answer, session);
            } else {
                // assume access token is JWT
                sessionAuthorizer.verifyJWT(exc, token, oauth2Answer, session);
            }

            doRedirect(exc, originalRequest, session);

            originalExchangeStore.postProcess(exc);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            exc.setResponse(Response.badRequest().body(e.getMessage()).build());
            originalExchangeStore.postProcess(exc);
            return true;
        }
    }

    private Map<String, Object> exchangeCodeForToken(String publicUrl, Map<String, String> params) throws Exception {

        String code = params.get("code");
        if (code == null) {
            throw new RuntimeException("No code received.");
        }

        Exchange e = auth.applyAuth(new Request.Builder()
                                .post(auth.getTokenEndpoint())
                                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                                .header(ACCEPT, APPLICATION_JSON)
                                .header(USER_AGENT, USERAGENT),
                        "code=" + code
                                + "&redirect_uri=" + publicUrl
                                + callbackPath + "&grant_type=authorization_code")
                .buildExchange();

        logHelper.handleRequest(e);

        Response response = auth.doRequest(e);

        logHelper.handleResponse(e);

        if (response.getStatusCode() != 200) {
            response.getBody().read();
            throw new RuntimeException("Authorization server returned " + response.getStatusCode() + ".");
        }

        if (!isJson(response)) {
            throw new RuntimeException("Token response is no JSON.");
        }

        return new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<>(){});
    }

    private OAuth2AnswerParameters initOAuth2AnswerParameters(Map<String, Object> json, String token) {
        OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
        oauth2Answer.setAccessToken(token);
        oauth2Answer.setTokenType((String) json.get("token_type"));
        oauth2Answer.setExpiration(numberToString(json.get("expires_in")));
        oauth2Answer.setRefreshToken((String) json.get("refresh_token"));
        LocalDateTime now = LocalDateTime.now();
        oauth2Answer.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (auth.idTokenIsValid((String) json.get("id_token"))) {
                oauth2Answer.setIdToken((String) json.get("id_token"));
            } else {
                oauth2Answer.setIdToken("INVALID");
            }
        }
        return oauth2Answer;
    }

    private static void doRedirect(Exchange exc, AbstractExchangeSnapshot originalRequest, Session session) throws JsonProcessingException {
        if (originalRequest.getRequest().getMethod().equals("GET")) {
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(), false).build());
        } else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);

            session.put(OAuthUtils.oa2redictKeyNameInSession(oa2redirect), new ObjectMapper().writeValueAsString(originalRequest));

            String delimiter = originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri() + delimiter + OA2REDIRECT + "=" + oa2redirect, false).build());
        }
    }
}
