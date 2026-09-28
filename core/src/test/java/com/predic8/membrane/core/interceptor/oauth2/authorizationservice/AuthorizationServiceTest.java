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

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import static com.predic8.membrane.core.http.Response.badRequest;
import static com.predic8.membrane.core.http.Response.serviceUnavailable;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    /**
     * A 5xx is not a verdict on the token, so it has to look like an unreachable server to the caller.
     * Anything else logs out every user whose token is refreshed while the authorization server, or a
     * proxy in front of it, is having trouble.
     */
    @Test
    void serverErrorOnTokenEndpointIsACommunicationError() {
        AuthorizationService auth = answering(serviceUnavailable("down").build());

        OAuth2Exception e = assertThrows(OAuth2Exception.class,
                () -> auth.refreshTokenRequest(session(), null, "the-refresh-token"));

        assertEquals(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR, e.getError());
    }

    /**
     * A 4xx is a verdict: the refresh token was rejected and the flow has to start over, so this must
     * not be softened into a communication error.
     */
    @Test
    void clientErrorOnTokenEndpointStaysAHardFailure() {
        AuthorizationService auth = answering(badRequest().build());

        Exception e = assertThrows(Exception.class,
                () -> auth.refreshTokenRequest(session(), null, "the-refresh-token"));

        assertFalse(e instanceof OAuth2Exception, "a rejected refresh token must not look like an outage");
    }

    private static Session session() {
        return new Session("username", new HashMap<>());
    }

    private static AuthorizationService answering(Response response) {
        return new StubAuthorizationService(response);
    }

    /**
     * Answers every call to the authorization server with one canned response, so the status handling
     * can be tested without an HTTP client.
     */
    private static class StubAuthorizationService extends AuthorizationService {

        private final Response response;

        StubAuthorizationService(Response response) {
            this.response = response;
            this.log = LoggerFactory.getLogger(StubAuthorizationService.class);
        }

        @Override
        public Response doRequest(Exchange exchange) {
            return response;
        }

        @Override
        public void init() {
        }

        @Override
        public String getIssuer() {
            return "http://localhost:1/issuer";
        }

        @Override
        public String getJwksEndpoint() {
            return "http://localhost:1/certs";
        }

        @Override
        public String getEndSessionEndpoint() {
            return "http://localhost:1/logout";
        }

        @Override
        public String getLoginURL(String callbackURL) {
            return "http://localhost:1/auth";
        }

        @Override
        public String getUserInfoEndpoint() {
            return "http://localhost:1/userinfo";
        }

        @Override
        public String getSubject() {
            return "sub";
        }

        @Override
        protected String getTokenEndpoint() {
            return "http://localhost:1/token";
        }

        @Override
        public String getRevocationEndpoint() {
            return "http://localhost:1/revoke";
        }
    }
}
