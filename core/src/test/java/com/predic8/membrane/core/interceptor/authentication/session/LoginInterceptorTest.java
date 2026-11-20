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

package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.authentication.session.SessionManager.Session;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginInterceptorTest {

    /**
     * A backend that maps "_" and "-" to the same variable, as CGI and many WSGI servers do, would otherwise see the
     * client's value next to the one of the logged-in user, see Traefik CVE-2026-54763.
     */
    @Test
    void identityHeaderFromTheClientIsRemovedInEverySpelling() throws Exception {
        final var session = new Session();
        session.preAuthorize("alice", Map.of("headerX-User", "alice"));
        final var exc = new Request.Builder()
                .get("/foo")
                .header("X_User", "admin")
                .header("x-user", "admin")
                .header("X-Other", "kept")
                .buildExchange();

        LoginInterceptor.applyBackendAuthorization(exc, session);

        assertEquals(List.of("X-Other: kept", "X-User: alice"),
                exc.getRequest().getHeader().getFields().stream()
                        .map(field -> field.getHeaderName() + ": " + field.getValue())
                        .filter(field -> !field.startsWith("Host"))
                        .toList());
    }

    @Test
    void identityHeadersThatDifferOnlyByUnderscoreAreBothKept() throws Exception {
        final var session = new Session();
        session.preAuthorize("alice", Map.of("headerX-User", "alice", "headerX_User", "bob"));
        final var exc = new Request.Builder().get("/foo").buildExchange();

        LoginInterceptor.applyBackendAuthorization(exc, session);

        assertEquals(List.of("X-User: alice", "X_User: bob"),
                exc.getRequest().getHeader().getFields().stream()
                        .map(field -> field.getHeaderName() + ": " + field.getValue())
                        .filter(field -> !field.startsWith("Host"))
                        .sorted()
                        .toList());
    }
}
