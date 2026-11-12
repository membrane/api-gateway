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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.test.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.net.*;
import java.nio.*;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerHandlerTest {

    private static final int PORT = 3077;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static Router router;

    @BeforeAll
    static void startTlsListener() throws Exception {
        router = new TestRouter();
        router.getConfiguration().setHotDeploy(false);
        final var sp = new ServiceProxy();
        sp.setPort(PORT);
        final var ks = new KeyStore();
        ks.setLocation("classpath:/alias-keystore.p12");
        ks.setKeyPassword("secret");
        ks.setKeyAlias("key1");
        final var ssl = new SSLParser();
        ssl.setKeyStore(ks);
        sp.setSslInboundParser(ssl);
        router.add(sp);
        router.start();
    }

    @AfterAll
    static void stopTlsListener() {
        router.stop();
    }

    private static final String HANDSHAKE_FAILED = "TLS handshake with client failed: ";

    /**
     * A client that sends its ClientHello and then hangs up makes the JDK report "Remote host terminated the
     * handshake". The log line must say that the handshake with the client failed, because the JDK message alone
     * reads as if the backend was the problem.
     */
    @Test
    void clientHangingUpDuringHandshakeIsLogged() throws Exception {
        final var log = startCapture();
        try {
            sendClientHelloAndHangUp();
            log.awaitContainsOrThrow(HANDSHAKE_FAILED + "Remote host terminated the handshake", TIMEOUT);
        } finally {
            stopCapture(log);
        }
    }

    /**
     * The client aborts the handshake because it does not trust the certificate. Depending on timing the server sees
     * the client's alert or a reset connection, before the fix the latter was only logged at debug.
     */
    @Test
    void clientRejectingTheCertificateIsLogged() throws Exception {
        final var log = startCapture();
        try {
            // The JDK default trust store does not trust the test certificate.
            try (final var socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket("localhost", PORT)) {
                assertThrows(SSLHandshakeException.class, socket::startHandshake);
            }
            log.awaitContainsOrThrow(HANDSHAKE_FAILED, TIMEOUT);
        } finally {
            stopCapture(log);
        }
    }

    /**
     * Closing an established connection without sending a request, e.g. a browser's unused preconnect, is not a
     * failed handshake.
     */
    @Test
    void closingAfterSuccessfulHandshakeIsNotLogged() throws Exception {
        final var log = startCapture();
        try {
            try (final var socket = (SSLSocket) trustingSocketFactory().createSocket("localhost", PORT)) {
                socket.startHandshake();
                socket.shutdownOutput();
                socket.getInputStream().readAllBytes();
            }
            // A failing connection afterwards, so the server has handled the first one by the time its line is logged.
            sendClientHelloAndHangUp();
            log.awaitContainsOrThrow(HANDSHAKE_FAILED, TIMEOUT);
            assertEquals(1, log.eventsContaining(HANDSHAKE_FAILED).size(), log.getMessages().toString());
        } finally {
            stopCapture(log);
        }
    }

    private static TestAppender startCapture() {
        final var appender = new TestAppender("tlsHandshake");
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
        return appender;
    }

    private static void stopCapture(TestAppender appender) {
        ((Logger) LogManager.getRootLogger()).removeAppender(appender);
        appender.stop();
    }

    private static SSLSocketFactory trustingSocketFactory() throws Exception {
        final var trustStore = java.security.KeyStore.getInstance("PKCS12");
        try (final var in = HttpServerHandlerTest.class.getResourceAsStream("/alias-truststore.p12")) {
            trustStore.load(in, "secret".toCharArray());
        }
        final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        final var context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    private static void sendClientHelloAndHangUp() throws Exception {
        final var engine = SSLContext.getDefault().createSSLEngine("localhost", PORT);
        engine.setUseClientMode(true);
        final var clientHello = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        engine.wrap(ByteBuffer.allocate(0), clientHello);
        clientHello.flip();

        try (final var socket = new Socket("localhost", PORT)) {
            socket.getOutputStream().write(clientHello.array(), 0, clientHello.limit());
            // Half-close (FIN, not RST), then drain the server's flight until it closes the connection.
            socket.shutdownOutput();
            socket.getInputStream().readAllBytes();
        }
    }
}
