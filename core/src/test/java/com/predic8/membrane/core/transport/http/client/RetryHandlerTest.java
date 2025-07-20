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

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.ok;
import static org.junit.jupiter.api.Assertions.*;

class RetryHandlerTest {

    RetryHandler rh;

    @BeforeEach
    void setUp() {
        rh = new RetryHandler();
        rh.setRetries(2);
    }


    @Test
    void noRetries() throws Exception {
        rh.setRetries(0);
        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(false);
        rh.executeWithRetries(get("/foo").buildExchange(), false, mock);
        assertEquals(1, mock.attempts);
    }

    @Test
    void twoRetries() throws Exception {
        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(false);
        rh.executeWithRetries(get("/foo").buildExchange(), false, mock);
        assertEquals(3, mock.attempts);
    }

    @Test
    void success() throws Exception {
        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(true);
        rh.executeWithRetries(get("/foo").buildExchange(), false, mock);
        assertEquals(1, mock.attempts);
    }

    @Nested
    class ExceptionIsThrown {

        @Test
        void socketExceptionGet() {
            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(new SocketException("Not today!"));
            assertThrows(SocketException.class, () -> rh.executeWithRetries(get("/foo").buildExchange(), false, mock));
            assertEquals(3, mock.attempts);
        }

        @Test
        void socketExceptionPost() {
            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(new SocketException("Problem!"));
            assertThrows(SocketException.class, () -> rh.executeWithRetries(post("/foo").buildExchange(), false, mock));
            assertEquals(1, mock.attempts);
        }

        @Test
        void connectionRefusedOneNode() {
            RetryHandler rh = new RetryHandler();
            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(new ConnectException("Firewall blocks!"));
            assertThrows(ConnectException.class, () -> rh.executeWithRetries(post("/foo").buildExchange(), false, mock));
            assertEquals(1, mock.attempts);
        }

    }

    @Test
    void internalError() throws Exception {
        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(501);
        rh.executeWithRetries(get("/foo").buildExchange(), true, mock);
        assertEquals(3, mock.attempts);
    }

    @Nested()
    class Balancing {

        @Test
        void internalError() throws Exception {
            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(501);
            Exchange exc = get("/foo").buildExchange();
            List<String> destinations = List.of("http://node1.example.com/", "http://node2.example.com/", "http://node3.example.com/");
            exc.setDestinations(destinations);
            rh.executeWithRetries(exc, true, mock);
            assertEquals(3, mock.attempts);
            assertEquals(destinations, mock.destinations);
        }

    }


    static class RetryableExchangeCallMock implements RetryableCall {

        int attempts;
        boolean success;
        Exception exception;
        int statusCode;
        List<String> destinations = new ArrayList<>();

        public RetryableExchangeCallMock(boolean success) {
            this.success = success;
        }

        public RetryableExchangeCallMock(Exception exception) {
            this.exception = exception;
        }

        public RetryableExchangeCallMock(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public boolean execute(Exchange exc, String dest, int attempt) throws Exception {
            attempts++;

            destinations.add(dest);

            if (exception != null) {
                throw exception;
            }

            if (statusCode != 0) {
                exc.setResponse(Response.statusCode(statusCode).build());
                return true;
            }

            if (!success) {
                return false;
            }

            exc.setResponse(ok().build());
            return success;
        }
    }
}
