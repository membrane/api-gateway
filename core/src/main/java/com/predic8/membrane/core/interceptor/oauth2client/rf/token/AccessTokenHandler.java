package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils;
import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;

public class AccessTokenHandler {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenHandler.class);

    public static final String OAUTH2_ANSWER = "oauth2Answer";

    private final Session session;
    private JwtKeyCertHandler jwtKeyCertHandler;
    private final AuthorizationService auth;
    private final OAuth2AnswerParameters params;
    private final ObjectMapper om = new ObjectMapper();
    private final Cache<String, Object> synchronizers = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build();


    public AccessTokenHandler(Session session, AuthorizationService auth) {
        this.session = session;
        this.auth = auth;

        try {
            this.params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        } catch (IOException e) {
            throw new RuntimeException("Could not deserialize OAuth2 answer");
        }
    }

    private void refreshAccessToken(Session session) throws Exception {
        if (!refreshingOfAccessTokenIsNeeded(session))
            return;

        Exchange refreshTokenExchange = auth.applyAuth(new Request.Builder()
                        .post(auth.getTokenEndpoint())
                        .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                        .header(ACCEPT, APPLICATION_JSON)
                        .header(USER_AGENT, USERAGENT),
                "grant_type=refresh_token"
                        + "&refresh_token=" + params.getRefreshToken())
                .buildExchange();

        Response refreshTokenResponse = auth.doRequest(refreshTokenExchange);
        if (!refreshTokenResponse.isOk()) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode from authorization server for refresh token request: " + refreshTokenResponse.getStatusCode());
        }
        if (!JsonUtils.isJson(refreshTokenResponse))
            throw new RuntimeException("Refresh Token response is no JSON.");

        @SuppressWarnings("unchecked")
        Map<String, Object> json = om.readValue(refreshTokenResponse.getBodyAsStreamDecoded(), Map.class);

        if (json.get("access_token") == null || json.get("refresh_token") == null) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + refreshTokenResponse.getStatusCode());
        }

        params.setAccessToken((String) json.get("access_token"));
        params.setRefreshToken((String) json.get("refresh_token"));
        params.setExpiration(JsonUtils.numberToString(json.get("expires_in")));
        LocalDateTime now = LocalDateTime.now();
        params.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (idTokenIsValid((String) json.get("id_token")))
                params.setIdToken((String) json.get("id_token"));
            else
                params.setIdToken("INVALID");
        }

        session.put(OAUTH2_ANSWER, params.serialize());
    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session) throws IOException {
        if (session.get(OAUTH2_ANSWER) == null)
            return false;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));

        var expiration = oauth2Params.getExpiration();

        if (isNullOrEmpty(oauth2Params.getRefreshToken(), expiration)) {
            return false;
        }

        var expirationTime = oauth2Params.getReceivedAt().plusSeconds(Long.parseLong(expiration)).minusSeconds(5);

        return LocalDateTime.now().isAfter(expirationTime);
    }

    private boolean isNullOrEmpty(String... values) {
        return Arrays.stream(values).anyMatch(value -> value == null || value.isEmpty());
    }

    private boolean idTokenIsValid(String idToken) {
        try {
            JwtGenerator.getClaimsFromSignedIdToken(idToken, auth.getIssuer(), auth.getClientId(), auth.getJwksEndpoint(), auth);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Object getTokenSynchronizer(Session session) {
        OAuth2AnswerParameters oauth2Params;
        try {
            oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String rt = oauth2Params.getRefreshToken();
        if (rt == null) return new Object();

        try {
            return synchronizers.get(rt, Object::new);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


}
