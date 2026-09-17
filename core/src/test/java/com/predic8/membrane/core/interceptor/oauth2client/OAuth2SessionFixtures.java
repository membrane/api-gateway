/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.session.Session;

import java.time.LocalDateTime;
import java.util.HashMap;

/**
 * Sessions in the states the OAuth2 client tests need. Shared by the test of the interceptor and the
 * tests of the parts it delegates to, so all of them describe the same session in the same way.
 */
public final class OAuth2SessionFixtures {

    public static final String USERNAME = "alice";
    public static final String ACCESS_TOKEN = "the-access-token";
    public static final String REFRESH_TOKEN = "the-refresh-token";

    private OAuth2SessionFixtures() {
    }

    /**
     * Authenticated, holding an access token that expired an hour ago and the refresh token to renew
     * it. This is the state in which a request reaches the authorization server.
     */
    public static Session expiredSession() throws Exception {
        return sessionWith("60", LocalDateTime.now().minusHours(1));
    }

    /**
     * Authenticated, holding an access token that is good for another hour.
     */
    public static Session validSession() throws Exception {
        return sessionWith("3600", LocalDateTime.now());
    }

    public static Session sessionWith(String expiresInSeconds, LocalDateTime receivedAt) throws Exception {
        OAuth2AnswerParameters params = new OAuth2AnswerParameters();
        params.setAccessToken(ACCESS_TOKEN);
        params.setRefreshToken(REFRESH_TOKEN);
        params.setExpiration(expiresInSeconds);
        params.setReceivedAt(receivedAt);

        Session session = new Session("username", new HashMap<>());
        session.setOAuth2Answer(params.serialize());
        session.setAccessToken(null, ACCESS_TOKEN);
        session.authorize(USERNAME);
        return session;
    }
}
