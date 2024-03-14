/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
