package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.jwt.Jwks;
import com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Map;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;

public class UserInfoHandler {
    private static final Logger log = LoggerFactory.getLogger(UserInfoHandler.class);
    public static final String OAUTH2_ANSWER = "oauth2Answer";
    private final boolean skip;
    private final AuthorizationService auth;
    private final Router router;
    private JwtAuthInterceptor jwtAuthInterceptor;

    public UserInfoHandler(boolean skip, AuthorizationService auth, Router router) {
        this.skip = skip;
        this.auth = auth;
        this.router = router;

        if (skip) {
            try {
                jwtAuthInterceptor = createJwtAuthInterceptor();
            } catch (Exception e) {
                throw new RuntimeException("Could not create JWTAuthInterceptor");
            }
        }
    }

//    public Outcome machtWas(Exchange exc, Session session, SessionManager sessionManager) throws Exception {
//        if (skip) {
//            return null;
//        }
//
//        if (session == null || !session.isVerified()) {
//            String auth = exc.getRequest().getHeader().getFirstValue(AUTHORIZATION);
//
//            if (auth != null && auth.substring(0, 7).equalsIgnoreCase("Bearer ")) {
//                session = sessionManager.getSession(exc);
//                session.put(ParamNames.ACCESS_TOKEN, auth.substring(7));
//                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
//                oauth2Answer.setAccessToken(auth.substring(7));
//                oauth2Answer.setTokenType("Bearer");
//                Map<String, Object> userinfo = revalidateToken(oauth2Answer);
//
//                if (userinfo == null) {
//                    log.debug("userinfo is null, redirecting.");
//                    return respondWithRedirect(exc);
//                }
//
//                oauth2Answer.setUserinfo(userinfo);
//                session.put(OAUTH2_ANSWER,oauth2Answer.serialize());
//                processUserInfo(userinfo, session);
//
//            }
//        }
//        return null;
//    }
//
    private void processUserInfo(Map<String, Object> userInfo, Session session) {
        if (!userInfo.containsKey(auth.getSubject()))
            throw new RuntimeException("User object does not contain " + auth.getSubject() + " key.");

        Map<String, Object> userAttributes = session.get();
        String userIdPropertyFixed = auth.getSubject().substring(0, 1).toUpperCase() + auth.getSubject().substring(1);
        String username = (String) userInfo.get(auth.getSubject());
        userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, username);

        session.authorize(username);
    }

//    public Outcome respondWithRedirect(Exchange exc) throws Exception {
//        String state = new BigInteger(130, new SecureRandom()).toString(32);
//
//        exc.setResponse(Response.redirect(auth.getLoginURL(state, publicUrlStuff.getPublicURL(exc, getAuthService()) + callbackPath, exc.getRequestURI()),false).build());
//
//        readBodyFromStreamIntoMemory(exc);
//
//        Session session = getSessionManager().getSession(exc);
//
//        originalExchangeStore.store(exc, session, state, exc);
//
//        if(session.get().containsKey(ParamNames.STATE))
//            state = session.get(ParamNames.STATE) + SESSION_VALUE_SEPARATOR + state;
//        session.put(ParamNames.STATE,state);
//
//        return Outcome.RETURN;
//    }

    public JwtAuthInterceptor getJwtAuthInterceptor() {
        return jwtAuthInterceptor;
    }

    public boolean isSkip() {
        return skip;
    }

    public Map<String, Object> revalidateToken(OAuth2AnswerParameters params, OAuth2Statistics statistics) throws Exception {

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
            if (!JsonUtils.isJson(response2))
                throw new RuntimeException("Response is no JSON.");

            //noinspection unchecked
            return new ObjectMapper().readValue(response2.getBodyAsStreamDecoded(), Map.class);
        }
    }

    private JwtAuthInterceptor createJwtAuthInterceptor() throws Exception {
        var jwtAuthInterceptor = new JwtAuthInterceptor();
        jwtAuthInterceptor.setJwks(createJwks(auth));
        jwtAuthInterceptor.setExpectedAud("any!!");
        jwtAuthInterceptor.init(router);
        return jwtAuthInterceptor;
    }

    private Jwks createJwks(AuthorizationService auth) throws Exception {
        var jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        jwks.setJwksUris(auth.getJwksEndpoint());
        return jwks;
    }
}
