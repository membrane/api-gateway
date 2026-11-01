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
package com.predic8.membrane.core.interceptor.oauth2client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_HOST;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_PROTO;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.communicationError;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.USERNAME;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.expiredSession;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.validSession;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2Resource2Interceptor.withoutOA2RedirectParameter;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuthUtils.oa2redictKeyNameInSession;
import static com.predic8.membrane.core.http.BodyCollectingMessageObserver.Strategy.ERROR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2Resource2InterceptorTest {

    private OAuth2Resource2Interceptor oauth2;
    private AuthorizationService auth;

    @BeforeEach
    void setup() throws Exception {
        oauth2 = new OAuth2Resource2Interceptor();

        auth = mock(AuthorizationService.class);
        oauth2.setAuthService(auth);

        oauth2.setLogoutUrl("/login/logout");
        oauth2.setAfterLogoutUrl("/uebersicht");
        oauth2.setAppendAccessTokenToRequest(true);

        oauth2.init(new TestRouter());
    }

    @Test
    void login() throws Exception {
        var exc = new Request.Builder()
                .get("/login")
                .buildExchange();
        exc.setOriginalRequestUri("/login");

        oauth2.handleRequest(exc);
    }

    @Test
    void logout() throws Exception {
        var exc = new Request.Builder()
                .get("/login/logout")
                .buildExchange();
        exc.setOriginalRequestUri("/login/logout");

        oauth2.handleRequestInternal(exc);
    }

    @Test
    void unreachableAuthorizationServerKeepsTheSession() throws Exception {
        when(auth.refreshTokenRequest(any(), any(), anyString())).thenThrow(communicationError());

        Session session = expiredSession();
        Exchange exc = requestWith(session);

        oauth2.handleRequestInternal(exc);

        assertTrue(session.isVerified(), "an unreachable authorization server logged the user out");
        assertEquals(USERNAME, session.getUsername());
        assertFalse(session.get().isEmpty(), "the session was cleared");
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    /**
     * The snapshot of the original request may come from a cookie the browser controls, so its forwarded
     * headers must not select the upstream the restored request is sent to.
     */
    @Test
    void restoredRequestKeepsTheDestinationOfTheRoute() throws Exception {
        final var original = new Request.Builder()
                .post("/foo?a=b")
                .header(X_FORWARDED_PROTO, "http")
                .header(X_FORWARDED_HOST, "internal.example.com")
                .body("demobody")
                .buildExchange();
        original.setProxy(new ServiceProxy(new ServiceProxyKey(2000), "backend.example.com", 80));
        final var session = validSession();
        session.put(oa2redictKeyNameInSession("xyz"),
                new ObjectMapper().writeValueAsString(new AbstractExchangeSnapshot(original, REQUEST, null, ERROR, 3000)));

        final var exc = new Request.Builder().get("http://backend.example.com/foo?a=b&oa2redirect=xyz").buildExchange();
        exc.setOriginalRequestUri("/foo?a=b&oa2redirect=xyz");
        exc.setProperty(SessionManager.SESSION, session);

        oauth2.handleRequestInternal(exc);

        assertEquals(List.of("http://backend.example.com/foo?a=b"), exc.getDestinations());
        assertEquals("/foo?a=b", exc.getOriginalRequestUri());
        assertEquals("POST", exc.getRequest().getMethod());
        assertEquals("demobody", exc.getRequest().getBodyAsStringDecoded());
    }

    /**
     * The forwarded headers in the snapshot may have been edited in the browser; the restored request carries
     * the ones the gateway set on the current request.
     */
    @Test
    void restoredRequestKeepsTheForwardedHeadersOfTheCurrentRequest() throws Exception {
        final var original = new Request.Builder()
                .post("/foo")
                .header(X_FORWARDED_FOR, "10.0.0.1")
                .header(X_FORWARDED_PROTO, "http")
                .header(X_FORWARDED_HOST, "internal.example.com")
                .body("demobody")
                .buildExchange();
        original.setProxy(new ServiceProxy(new ServiceProxyKey(2000), "backend.example.com", 80));
        final var session = validSession();
        session.put(oa2redictKeyNameInSession("xyz"),
                new ObjectMapper().writeValueAsString(new AbstractExchangeSnapshot(original, REQUEST, null, ERROR, 3000)));

        final var exc = new Request.Builder()
                .get("http://backend.example.com/foo?oa2redirect=xyz")
                .header(X_FORWARDED_FOR, "203.0.113.7")
                .header(X_FORWARDED_PROTO, "https")
                .header(X_FORWARDED_HOST, "gateway.example.com")
                .buildExchange();
        exc.setOriginalRequestUri("/foo?oa2redirect=xyz");
        exc.setProperty(SessionManager.SESSION, session);

        oauth2.handleRequestInternal(exc);

        final var header = exc.getRequest().getHeader();
        assertEquals("203.0.113.7", header.getValuesAsString(X_FORWARDED_FOR));
        assertEquals("https", header.getValuesAsString(X_FORWARDED_PROTO));
        assertEquals("gateway.example.com", header.getValuesAsString(X_FORWARDED_HOST));
        assertEquals("demobody", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void oa2RedirectParameterIsRemoved() {
        assertEquals("/foo", withoutOA2RedirectParameter("/foo?oa2redirect=xyz", "xyz"));
        assertEquals("/foo?a=b", withoutOA2RedirectParameter("/foo?a=b&oa2redirect=xyz", "xyz"));
        assertEquals("/foo?a=b", withoutOA2RedirectParameter("/foo?oa2redirect=xyz&a=b", "xyz"));
        assertEquals("/foo?a=b&c=d", withoutOA2RedirectParameter("/foo?a=b&oa2redirect=xyz&c=d", "xyz"));
    }

    private static Exchange requestWith(Session session) throws Exception {
        Exchange exc = new Request.Builder().get("/foo").buildExchange();
        exc.setOriginalRequestUri("/foo");
        exc.setProperty(SessionManager.SESSION, session);
        return exc;
    }
}
