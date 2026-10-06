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
package com.predic8.membrane.core.interceptor.stomp;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class STOMPClientTest {

    @Test
    void brokerConnectionFailureIsGatewayError() throws Exception {
        final var closedPort = freePort();

        STOMPClient client = new STOMPClient();
        client.setHost("127.0.0.1");
        client.setPort(closedPort);
        client.init(new DummyTestRouter());

        Exchange exc = Request.get("/")
                .header("login", "user")
                .header("passcode", "secret")
                .buildExchange();

        assertEquals(ABORT, client.handleRequest(exc));
        assertEquals(502, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("\"type\""));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("/gateway"));
    }
}
