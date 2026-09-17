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

package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2Exception;
import com.predic8.membrane.core.interceptor.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static com.predic8.membrane.core.http.Response.serviceUnavailable;
import static com.predic8.membrane.core.http.Response.unauthorized;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService.MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR;
import static com.predic8.membrane.core.interceptor.oauth2client.OAuth2SessionFixtures.validSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenRevalidatorTest {

    private AuthorizationService auth;
    private AccessTokenRevalidator revalidator;

    @BeforeEach
    void setUp() {
        auth = mock(AuthorizationService.class);
        revalidator = new AccessTokenRevalidator();
        revalidator.init(auth, new OAuth2Statistics());
        revalidator.setRevalidateTokenAfter(0);
    }

    /**
     * A 5xx from the user endpoint says nothing about the token. Treating it as a rejection would log
     * out every user whose token happened to be revalidated while the authorization server, or a proxy
     * in front of it, was having trouble.
     */
    @Test
    void serverErrorKeepsTheSession() throws Exception {
        when(auth.requestUserEndpoint(any(), any())).thenReturn(serviceUnavailable("down").build());
        Session session = validSession();

        OAuth2Exception e = assertThrows(OAuth2Exception.class, () -> revalidator.revalidateIfNeeded(session, null));

        assertEquals(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR, e.getError());
        assertTrue(session.isVerified());
    }

    /**
     * A server that could not be reached at all is the same situation as one answering 5xx, so it has
     * to end in the same OAuth2 error rather than in a raw transport exception.
     */
    @Test
    void unreachableServerKeepsTheSession() throws Exception {
        when(auth.requestUserEndpoint(any(), any())).thenThrow(new ConnectException("Connection refused"));
        Session session = validSession();

        OAuth2Exception e = assertThrows(OAuth2Exception.class, () -> revalidator.revalidateIfNeeded(session, null));

        assertEquals(MEMBRANE_OAUTH2_SERVER_COMMUNICATION_ERROR, e.getError());
        assertTrue(session.isVerified());
    }

    /**
     * A rejection is an answer, so the session really is done and the flow has to start over.
     */
    @Test
    void rejectedTokenClearsTheSession() throws Exception {
        when(auth.requestUserEndpoint(any(), any())).thenReturn(unauthorized().build());
        Session session = validSession();

        revalidator.revalidateIfNeeded(session, null);

        assertFalse(session.isVerified());
    }
}
