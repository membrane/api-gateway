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
package com.predic8.membrane.core.sslinterceptor;

import com.predic8.membrane.core.proxies.SSLProxy;
import com.predic8.membrane.core.transport.ssl.SSLExchange;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SSLLogInterceptorTest {

    private final SSLLogInterceptor interceptor = new SSLLogInterceptor();

    private Logger rootLogger;
    private TestAppender appender;

    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LogManager.getRootLogger();
        appender = new TestAppender("SSLLogInterceptorTest");
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.removeAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("Logs the proxy that matched, the client it came from and the backend it goes to")
    void logsConnection() {
        assertEquals(CONTINUE, interceptor.handleRequest(exchangeFor(sslProxy(443))));

        assertLogged("api.example.com:8443: TLS connection from 10.0.0.7:56441 to api.example.com:443");
    }

    @Test
    @DisplayName("A target without a port is forwarded to - and logged with - the port the proxy listens on")
    void logsTheListeningPortWhenTheTargetHasNone() {
        assertEquals(CONTINUE, interceptor.handleRequest(exchangeFor(sslProxy(-1))));

        assertLogged("api.example.com:8443: TLS connection from 10.0.0.7:56441 to api.example.com:8443");
    }

    private void assertLogged(String expected) {
        assertTrue(appender.contains(expected), appender.getMessages().toString());
    }

    private static SSLProxy sslProxy(int targetPort) {
        SSLProxy proxy = new SSLProxy();
        proxy.setHost("api.example.com");
        proxy.setPort(8443);
        SSLProxy.Target target = new SSLProxy.Target();
        target.setHost("api.example.com");
        target.setPort(targetPort);
        proxy.setTarget(target);
        return proxy;
    }

    private static SSLExchange exchangeFor(SSLProxy proxy) {
        SSLExchange exc = new SSLExchange();
        exc.setRule(proxy);
        exc.setRemoteAddrIp("10.0.0.7");
        exc.setRemotePort(56441);
        return exc;
    }
}
