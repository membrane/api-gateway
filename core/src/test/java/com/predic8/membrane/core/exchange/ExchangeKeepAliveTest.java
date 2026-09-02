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

package com.predic8.membrane.core.exchange;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.Connection;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A message whose body read failed leaves the connection desynchronized: the rest of the body is still
 * in the stream. Such a connection must neither be kept alive nor go back into the pool, no matter what
 * the keep-alive headers say.
 */
class ExchangeKeepAliveTest {

    @Test
    void fullyReadBodiesKeepTheConnectionAlive() {
        assertTrue(exchange(new Request(), new Response()).canKeepConnectionAlive());
    }

    @Test
    void failedRequestBodyPreventsKeepAlive() {
        Request request = new Request();
        failBody(request);

        assertFalse(exchange(request, new Response()).canKeepConnectionAlive());
    }

    @Test
    void failedResponseBodyPreventsKeepAlive() {
        Response response = new Response();
        failBody(response);

        assertFalse(exchange(new Request(), response).canKeepConnectionAlive());
    }

    @Test
    void closeTargetConnectionDetachesAndCloses() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Exchange exc = new Exchange(null);
            Connection con = Connection.open("localhost", server.getLocalPort(), null, null, 30000);
            exc.setTargetConnection(con);

            assertTrue(exc.closeTargetConnection());

            assertNull(exc.getTargetConnection(), "must be detached, so nothing hands it back to the pool");
            assertTrue(con.isClosed());
        }
    }

    @Test
    void closeTargetConnectionIsANoOpWithoutAConnection() {
        Exchange exc = new Exchange(null);

        assertFalse(exc.closeTargetConnection());
        assertNull(exc.getTargetConnection());
    }

    private static Exchange exchange(Request request, Response response) {
        Exchange exc = new Exchange(null);
        exc.setRequest(request);
        exc.setResponse(response);
        return exc;
    }

    /**
     * Puts the message's body into the failed state the way it happens in production: by reading from a
     * stream that dies mid-body.
     */
    private static void failBody(Message message) {
        Body body = new Body(ThrowingInputStream.closedChannel("partial"), 1000);
        message.setBody(body);

        assertThrows(ReadingBodyException.class, body::read);
        assertTrue(body.hasFailed());
    }
}
