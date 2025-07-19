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

    public static final int DELAY_BETWEEN_RETRIES_MS = 10;
    HttpClientConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new HttpClientConfiguration();
    }


    @Test
    void noRetries() throws Exception {

        RetryHandler rh = new RetryHandler(configuration, 0);

        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(false);
        rh.executeWithRetries(get("/foo").buildExchange(), false, mock);

        assertEquals(1, mock.attempts);
    }

    @Test
    void twoRetries() throws Exception {

        RetryHandler rh = new RetryHandler(configuration, 2);

        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(false);
        rh.executeWithRetries(get("/foo").buildExchange(), false, mock);

        assertEquals(3, mock.attempts);
    }

    @Test
    void success() throws Exception {

        RetryHandler rh = new RetryHandler(configuration, 2);

        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(true);
        rh.executeWithRetries(get("/foo").buildExchange(), false, mock);

        assertEquals(1, mock.attempts);
    }

    @Nested
    class ExceptionIsThrown {

        @Test
        void socketExceptionGet() throws Exception {

            RetryHandler rh = new RetryHandler(configuration, 2);

            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(new SocketException("Not today!"));

            assertThrows(SocketException.class, () -> rh.executeWithRetries(get("/foo").buildExchange(), false, mock));
            assertEquals(3, mock.attempts);
        }

        @Test
        void socketExceptionPost() {

            RetryHandler rh = new RetryHandler(configuration, 2);

            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(new SocketException("Problem!"));

            assertThrows(SocketException.class, () -> rh.executeWithRetries(post("/foo").buildExchange(), false, mock));

            assertEquals(1, mock.attempts);
        }

        @Test
        void connectionRefusedOneNode() {

            RetryHandler rh = new RetryHandler(configuration, 2);

            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(new ConnectException("Firewall blocks!"));

            assertThrows(ConnectException.class, () -> rh.executeWithRetries(post("/foo").buildExchange(), false, mock));

            assertEquals(1, mock.attempts);
        }

    }

    @Test
    void internalError() throws Exception {

        RetryHandler rh = new RetryHandler(configuration, 2);

        RetryableExchangeCallMock mock = new RetryableExchangeCallMock(501);
        rh.executeWithRetries(get("/foo").buildExchange(), true, mock);

        assertEquals(3, mock.attempts);
    }

    @Nested()
    class Balancing {

        @Test
        void internalError() throws Exception {

            RetryHandler rh = new RetryHandler(configuration, 2);

            RetryableExchangeCallMock mock = new RetryableExchangeCallMock(501);
            Exchange exc = get("/foo").buildExchange();
            exc.setDestinations(List.of("http://node1.example.com/", "http://node2.example.com/", "http://node3.example.com/"));
            rh.executeWithRetries(exc, true, mock);

            assertEquals(3, mock.attempts);
        }

    }


    class RetryableExchangeCallMock implements RetryableCall {

        int attempts;
        boolean success;
        Exception exception;
        int statusCode;

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
