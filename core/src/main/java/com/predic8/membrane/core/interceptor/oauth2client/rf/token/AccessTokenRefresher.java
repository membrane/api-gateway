package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenManager.idTokenIsValid;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.OAUTH2_ANSWER;

public class AccessTokenRefresher {

    public static boolean refreshingOfAccessTokenIsNeeded(Session session) throws IOException {
        if (session.get(OAUTH2_ANSWER) == null)
            return false;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        if (oauth2Params.getRefreshToken() == null || oauth2Params.getRefreshToken().isEmpty() || oauth2Params.getExpiration() == null || oauth2Params.getExpiration().isEmpty())
            return false;

        return LocalDateTime.now().isAfter(oauth2Params.getReceivedAt().plusSeconds(Long.parseLong(oauth2Params.getExpiration())).minusSeconds(5)); // refresh token 5 seconds before expiration
    }

    public static void refreshAccessToken(Session session, AuthorizationService auth) throws Exception {

        if (!refreshingOfAccessTokenIsNeeded(session)) return;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        Exchange refreshTokenExchange = auth.applyAuth(new Request.Builder().post(auth.getTokenEndpoint()).contentType(APPLICATION_X_WWW_FORM_URLENCODED).header(ACCEPT, APPLICATION_JSON).header(USER_AGENT, USERAGENT), "grant_type=refresh_token" + "&refresh_token=" + oauth2Params.getRefreshToken()).buildExchange();

        Response refreshTokenResponse = auth.doRequest(refreshTokenExchange);
        if (!refreshTokenResponse.isOk()) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode from authorization server for refresh token request: " + refreshTokenResponse.getStatusCode());
        }
        if (!isJson(refreshTokenResponse)) throw new RuntimeException("Refresh Token response is no JSON.");

        @SuppressWarnings("unchecked") Map<String, Object> json = new ObjectMapper().readValue(refreshTokenResponse.getBodyAsStreamDecoded(), Map.class);

        if (json.get("access_token") == null || json.get("refresh_token") == null) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + refreshTokenResponse.getStatusCode());
        }
        oauth2Params.setAccessToken((String) json.get("access_token"));
        oauth2Params.setRefreshToken((String) json.get("refresh_token"));
        oauth2Params.setExpiration(numberToString(json.get("expires_in")));
        LocalDateTime now = LocalDateTime.now();
        oauth2Params.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (idTokenIsValid((String) json.get("id_token"), auth))
                oauth2Params.setIdToken((String) json.get("id_token"));
            else oauth2Params.setIdToken("INVALID");
        }

        session.put(OAUTH2_ANSWER, oauth2Params.serialize());

    }
}
