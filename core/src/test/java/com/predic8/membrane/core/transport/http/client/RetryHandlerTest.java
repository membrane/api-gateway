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
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static org.junit.jupiter.api.Assertions.*;

class RetryHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(RetryHandlerTest.class);

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
    void tenRetries() throws Exception {
        rh.setRetries(10);
        rh.setDelay(2);
        rh.setBackoffMultiplier(1.5);
        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(503);
        rh.executeWithRetries(get("/foo").buildExchange(), true, mock);
        assertEquals(11, mock.attempts);
    }

    @Test
    void success() throws Exception {

        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(true);
        Exchange exc = get("/foo").buildExchange();

        HttpClientStatusEventListenerMock listener = registerHttpClientStatusEventBus(exc);

        rh.executeWithRetries(exc, false, mock);
        assertEquals(1, mock.attempts);

        assertEquals(200, listener.statusCodes.get("/foo"));
    }

    @Nested
    class ExceptionIsThrown {

        @Test
        void socketExceptionGet() throws Exception {
            SocketException exception = new SocketException("Not today!");
            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(exception);
            Exchange exc = get("/foo").buildExchange();
            HttpClientStatusEventListenerMock listener = registerHttpClientStatusEventBus(exc);

            assertThrows(SocketException.class, () -> rh.executeWithRetries(exc, false, mock));
            assertEquals(3, mock.attempts);

            assertEquals(exception, listener.exceptions.get("/foo"));
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

    @Nested
    class EqualsHashcode {

        @Test
        void equals_returnsFalseForDifferentRetries() {
            assertNotEquals(new RetryHandler(), new RetryHandler() {{
                setRetries(3);
            }});
        }

        @Test
        void equals_returnsFalseForDifferentDelay() {
            assertNotEquals(new RetryHandler(), new RetryHandler() {{
                setDelay(500);
            }});
        }

        @Test
        void equals_returnsFalseForDifferentBackoffMultiplier() {
            assertNotEquals(new RetryHandler(), new RetryHandler() {{
                setBackoffMultiplier(99.9);
            }});
        }

        @Test
        @DisplayName("Transitivity: if a==b and b==c then a==c")
        void equals_isTransitive() {
            RetryHandler a = new RetryHandler();
            RetryHandler b = new RetryHandler();
            RetryHandler c = new RetryHandler();
            assertTrue(a.equals(b) && b.equals(c) && a.equals(c));
        }
    }

    // TODO
    @Test
    void bus() throws Exception {
        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(200);
        Exchange exc = get("/foo").buildExchange();
        HttpClientStatusEventBus bus = new HttpClientStatusEventBus();
        bus.engageInstance(exc);
        List<String> destinations = List.of("http://node1.example.com/");
        exc.setDestinations(destinations);
        rh.executeWithRetries(exc, false, mock);

        // TODO Mock bus and see if called

    }

    private @NotNull HttpClientStatusEventListenerMock registerHttpClientStatusEventBus(Exchange exc) {
        HttpClientStatusEventListenerMock listenerMock = new HttpClientStatusEventListenerMock();
        HttpClientStatusEventBus.engage(exc);
        HttpClientStatusEventBus bus = HttpClientStatusEventBus.getHttpClientStatusEventBus(exc);
        bus.registerListener(listenerMock);
        return listenerMock;
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
                return false;
            }

            if (!success) {
                return false;
            }

            exc.setResponse(ok().build());
            return false;
        }
    }

    static class HttpClientStatusEventListenerMock implements HttpClientStatusEventListener {

        Map<String, Integer> statusCodes = new HashMap<>();
        Map<String, Exception> exceptions = new HashMap<>();

        @Override
        public void onResponse(long timestamp, String destination, int responseCode) {
            log.info("onResponse");
            statusCodes.put(destination, responseCode);
        }

        @Override
        public void onException(long timestamp, String destination, Exception exception) {
            log.info("onException");
            exceptions.put(destination, exception);
        }
    }
}
