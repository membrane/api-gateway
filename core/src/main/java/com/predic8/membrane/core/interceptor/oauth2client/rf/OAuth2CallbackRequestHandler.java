package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.LogInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
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
import static com.predic8.membrane.core.interceptor.oauth2client.rf.CSRFStuff.csrfTokenMatches;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.CSRFStuff.getSecurityTokenFromState;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.*;

public class OAuth2CallbackRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2CallbackRequestHandler.class);

    public static boolean machMal(
            URIFactory uriFactory,
            Exchange exc,
            Session session,
            AuthorizationService auth,
            OriginalExchangeStore originalExchangeStore,
            String publicURL,
            String callbackPath,
            AccessTokenRevalidator accessTokenRevalidator,
            OAuth2Statistics statistics,
            UserInfoHandler userInfoHandler
    ) {
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

            if (log.isDebugEnabled()) {
                logi.handleResponse(e);
            }

            if (!isJson(response)) {
                throw new RuntimeException("Token response is no JSON.");
            }

            Map<String, Object> json = new ObjectMapper().readValue(response.getBodyAsStreamDecoded(), new TypeReference<>(){});

            if (!json.containsKey("access_token")) {
                throw new RuntimeException("No access_token received.");
            }

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
                if (auth.idTokenIsValid((String) json.get("id_token"))) {
                    oauth2Answer.setIdToken((String) json.get("id_token"));
                } else {
                    oauth2Answer.setIdToken("INVALID");
                }
            }

            accessTokenRevalidator.getValidTokens().put(token, true);

            if (!userInfoHandler.isSkip()) {
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

                if (log.isDebugEnabled()) {
                    logi.handleResponse(e2);
                }

                if (response2.getStatusCode() != 200) {
                    statistics.accessTokenInvalid();
                    throw new RuntimeException("User data could not be retrieved.");
                }

                statistics.accessTokenValid();

                if (!isJson(response2)) {
                    throw new RuntimeException("Userinfo response is no JSON.");
                }

                Map<String, Object> json2 = new ObjectMapper().readValue(response2.getBodyAsStreamDecoded(), new TypeReference<>(){});

                oauth2Answer.setUserinfo(json2);

                session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

                userInfoHandler.processUserInfo(json2, session, auth);
            } else {
                session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

                // assume access token is JWT
                if (userInfoHandler.getJwtAuthInterceptor().handleJwt(exc, token) != Outcome.CONTINUE)
                    throw new RuntimeException("Access token is not a JWT.");

                userInfoHandler.processUserInfo((Map<String, Object>) exc.getProperty("jwt"), session, auth);
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

    private static void doRedirect(Exchange exc, AbstractExchangeSnapshot originalRequest, Session session) throws JsonProcessingException {
        if (originalRequest.getRequest().getMethod().equals("GET")) {
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri(), false).build());
        } else {
            String oa2redirect = new BigInteger(130, new SecureRandom()).toString(32);

            session.put(OAuthUtilsStuff.oa2redictKeyNameInSession(oa2redirect), new ObjectMapper().writeValueAsString(originalRequest));

            String delimiter = originalRequest.getOriginalRequestUri().contains("?") ? "&" : "?";
            exc.setResponse(Response.redirect(originalRequest.getOriginalRequestUri() + delimiter + OA2REDIRECT + "=" + oa2redirect, false).build());
        }
    }
}
