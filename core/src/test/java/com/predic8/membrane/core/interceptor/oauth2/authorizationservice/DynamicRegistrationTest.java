/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DynamicRegistrationTest {

    private DynamicRegistration reg;
    private FlowController flowController;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        flowController = mock(FlowController.class);
        httpClient = mock(HttpClient.class);

        HttpClientFactory factory = mock(HttpClientFactory.class);
        when(factory.createClient(any())).thenReturn(httpClient);

        Router router = mock(Router.class);
        when(router.getFlowController()).thenReturn(flowController);
        when(router.getHttpClientFactory()).thenReturn(factory);

        reg = new DynamicRegistration();
        reg.init(router);
    }

    private void stubCall(Response response) throws Exception {
        doAnswer(inv -> {
            ((Exchange) inv.getArgument(0)).setResponse(response);
            return null;
        }).when(httpClient).call(any());
    }

    @Test
    void happyPath() throws Exception {
        when(flowController.invokeRequestHandlers(any(), anyList())).thenReturn(CONTINUE);
        stubCall(Response.ok().body("{}").build());
        when(flowController.invokeResponseHandlers(any(), anyList())).thenReturn(CONTINUE);

        InputStream body = reg.retrieveOpenIDConfiguration("http://example.com/.well-known");
        assertNotNull(body);
    }

    @Test
    void responseFlowAbortThrows() throws Exception {
        when(flowController.invokeRequestHandlers(any(), anyList())).thenReturn(CONTINUE);
        stubCall(Response.ok().build());
        when(flowController.invokeResponseHandlers(any(), anyList())).thenReturn(ABORT);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reg.retrieveOpenIDConfiguration("http://example.com/.well-known"));
        assertTrue(ex.getMessage().contains("response"));
    }

    @Test
    void responseReplacedByInterceptorIsReturned() throws Exception {
        when(flowController.invokeRequestHandlers(any(), anyList())).thenReturn(CONTINUE);
        stubCall(Response.ok().body("stale").build());
        doAnswer(inv -> {
            ((Exchange) inv.getArgument(0)).setResponse(Response.ok().body("replaced").build());
            return CONTINUE;
        }).when(flowController).invokeResponseHandlers(any(), anyList());

        String body = new String(reg.retrieveOpenIDConfiguration("http://example.com/.well-known").readAllBytes());
        assertEquals("replaced", body);
    }
}
