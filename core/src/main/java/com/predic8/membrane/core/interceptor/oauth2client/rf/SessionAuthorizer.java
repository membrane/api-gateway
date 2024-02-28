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
package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.isJson;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.OAUTH2_ANSWER;

public class SessionAuthorizer {
    private static final Logger log = LoggerFactory.getLogger(SessionAuthorizer.class);

    LogHelper logHelper = new LogHelper();

    private JwtAuthInterceptor jwtAuthInterceptor;
    private boolean skip;

    private AuthorizationService auth;
    private Router router;
    private OAuth2Statistics statistics;

    public void init(AuthorizationService auth, Router router, OAuth2Statistics statistics) {
        this.auth = auth;
        this.router = router;
        this.statistics = statistics;

        if (skip) {
            try {
                this.jwtAuthInterceptor = createJwtAuthInterceptor();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean isSkipUserInfo() {
        return skip;
    }

    public void setSkipUserInfo(boolean skip) {
        this.skip = skip;
    }

    public void authorizeSession(Map<String, Object> userInfo, Session session, AuthorizationService auth) {
        if (!userInfo.containsKey(auth.getSubject()))
            throw new RuntimeException("User object does not contain " + auth.getSubject() + " key.");

        Map<String, Object> userAttributes = session.get();
        String userIdPropertyFixed = auth.getSubject().substring(0, 1).toUpperCase() + auth.getSubject().substring(1);
        String username = (String) userInfo.get(auth.getSubject());
        userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, username);

        session.authorize(username);
    }

    public JwtAuthInterceptor getJwtAuthInterceptor() {
        return jwtAuthInterceptor;
    }

    private JwtAuthInterceptor createJwtAuthInterceptor() throws Exception {
        var jwtAuthInterceptor = new JwtAuthInterceptor();

        jwtAuthInterceptor.setJwks(createJwks());
        jwtAuthInterceptor.setExpectedAud("any!!");
        jwtAuthInterceptor.init(router);

        return jwtAuthInterceptor;
    }

    private Jwks createJwks() throws Exception {
        var jwks = new Jwks();

        jwks.setJwks(new ArrayList<>());
        jwks.setJwksUris(auth.getJwksEndpoint());
        jwks.setAuthorizationService(auth);

        return jwks;
    }

    public void retrieveUserInfo(String tokenType, String token, OAuth2AnswerParameters oauth2Answer, Session session) throws Exception {
        Exchange e2 = new Request.Builder()
                .get(auth.getUserInfoEndpoint())
                .header("Authorization", tokenType + " " + token)
                .header("User-Agent", USERAGENT)
                .header(ACCEPT, APPLICATION_JSON)
                .buildExchange();

        logHelper.handleRequest(e2);

        Response response2 = auth.doRequest(e2);

        logHelper.handleResponse(e2);

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

        authorizeSession(json2, session, auth);

        session.put(OAUTH2_ANSWER, oauth2Answer.serialize());
    }

    public void verifyJWT(Exchange exc, String token, OAuth2AnswerParameters oauth2Answer, Session session) throws Exception {
        session.put(OAUTH2_ANSWER, oauth2Answer.serialize());

        if (getJwtAuthInterceptor().handleJwt(exc, token) != Outcome.CONTINUE)
            throw new RuntimeException("Access token is not a JWT.");

        authorizeSession((Map<String, Object>) exc.getProperty("jwt"), session, auth);

    }
}
