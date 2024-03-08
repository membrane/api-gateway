package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;

import java.time.LocalDateTime;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;

public class TokenResponseHandler {
    private AuthorizationService auth;

    public void init(AuthorizationService auth) {
        this.auth = auth;
    }

    public void handleTokenResponse(Session session, String wantedScope, Map<String, Object> json, OAuth2AnswerParameters oauth2Answer) {
        String accessToken = (String) json.get("access_token");
        oauth2Answer.setAccessToken(accessToken);
        if (accessToken != null)
            session.setAccessToken(wantedScope, accessToken); // saving for logout

        oauth2Answer.setTokenType((String) json.get("token_type"));
        oauth2Answer.setRefreshToken((String) json.get("refresh_token"));
        // TODO: "refresh_token_expires_in":1209600
        oauth2Answer.setExpiration(numberToString(json.get("expires_in")));
        LocalDateTime now = LocalDateTime.now();
        oauth2Answer.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (auth.idTokenIsValid((String) json.get("id_token"))) {
                oauth2Answer.setIdToken((String) json.get("id_token"));
            } else {
                oauth2Answer.setIdToken("INVALID");
            }
        }
    }

}
