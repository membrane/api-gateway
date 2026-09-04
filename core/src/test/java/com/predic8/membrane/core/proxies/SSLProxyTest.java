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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.sslinterceptor.SSLInterceptor;
import com.predic8.membrane.core.transport.ssl.SSLExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.predic8.membrane.core.router.YamlRouterBootstrap.loadIntoRouter;
import static com.predic8.membrane.core.transport.ssl.TLSError.access_denied;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.freePort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class SSLProxyTest {

    private static final String SNI_HOST = "sni.example.com";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @TempDir
    Path tempDir;

    private ServerSocket backend;
    private TestRouter router;

    @BeforeEach
    void setUp() throws IOException {
        backend = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (router != null)
            router.stop();
        backend.close();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("A TLS connection whose SNI matches the sslProxy host is forwarded unterminated")
    void forwardsConnectionWithMatchingSni() throws Exception {
        Future<byte[]> forwarded = executor.submit(this::readFirstBytesFromBackend);
        int port = startRouterWithSSLProxy();

        sendClientHello(port, SNI_HOST);

        byte[] clientHello = forwarded.get(5, SECONDS);
        assertEquals(0x16, clientHello[0], "expected a TLS handshake record");
        assertTrue(new String(clientHello, UTF_8).contains(SNI_HOST), "expected the client's SNI to arrive at the target");
    }

    @Test
    @DisplayName("A TLS connection whose SNI does not match the sslProxy host is not forwarded")
    void doesNotForwardConnectionWithOtherSni() throws Exception {
        Future<byte[]> forwarded = executor.submit(this::readFirstBytesFromBackend);
        int port = startRouterWithSSLProxy();

        IOException handshakeFailure = sendClientHello(port, "other.example.com");

        assertThrows(TimeoutException.class, () -> forwarded.get(1, SECONDS));
        assertNotNull(handshakeFailure);
        assertTrue(handshakeFailure.getMessage().contains("unrecognized_name"), handshakeFailure.getMessage());
    }

    @Test
    @DisplayName("An sslProxy without a host is rejected: without a server name to match there is nothing to route on")
    void rejectsConfigurationWithoutHost() throws Exception {
        Path config = Files.writeString(tempDir.resolve("apis.yaml"), """
                sslProxy:
                  port: %d
                  target:
                    host: localhost
                    port: 443
                """.formatted(freePort()));

        var e = assertThrows(ConfigurationParsingException.class,
                () -> loadIntoRouter(new DefaultRouter(), config.toString()));

        assertTrue(e.getMessage().contains("host"), e.getMessage());
    }

    @Test
    @DisplayName("An interceptor that rejects a connection ends it with the TLS alert it set")
    void sendsTheAlertTheInterceptorSet() throws Exception {
        Future<byte[]> forwarded = executor.submit(this::readFirstBytesFromBackend);
        int port = startRouterWithSSLProxy(rejectWith(exc -> exc.setError(access_denied)));

        IOException handshakeFailure = sendClientHello(port, SNI_HOST);

        assertThrows(TimeoutException.class, () -> forwarded.get(1, SECONDS));
        assertNotNull(handshakeFailure);
        assertTrue(handshakeFailure.getMessage().contains("access_denied"), handshakeFailure.getMessage());
    }

    @Test
    @DisplayName("An interceptor that rejects without setting an error still ends with an alert, not an internal failure")
    void fallsBackToInternalErrorWhenTheInterceptorSetNoError() throws Exception {
        Future<byte[]> forwarded = executor.submit(this::readFirstBytesFromBackend);
        int port = startRouterWithSSLProxy(rejectWith(exc -> {}));

        IOException handshakeFailure = sendClientHello(port, SNI_HOST);

        assertThrows(TimeoutException.class, () -> forwarded.get(1, SECONDS));
        assertNotNull(handshakeFailure);
        assertTrue(handshakeFailure.getMessage().contains("internal_error"), handshakeFailure.getMessage());
    }

    @Test
    @DisplayName("A target without a port is forwarded to the port the proxy listens on")
    void targetPortFallsBackToTheListeningPort() {
        SSLProxy proxy = createSSLProxy(8443);
        assertEquals(backend.getLocalPort(), proxy.getTargetPort());

        proxy.getTarget().setPort(-1);
        assertEquals(8443, proxy.getTargetPort());
    }

    private static SSLInterceptor rejectWith(Consumer<SSLExchange> prepare) {
        return new SSLInterceptor() {
            @Override
            public void init(Router router) {
            }

            @Override
            public Outcome handleRequest(SSLExchange exc) {
                prepare.accept(exc);
                return Outcome.ABORT;
            }
        };
    }

    private int startRouterWithSSLProxy(SSLInterceptor... sslInterceptors) throws IOException {
        int port = freePort();
        router = new TestRouter();
        router.add(createSSLProxy(port, sslInterceptors));
        router.start();
        return port;
    }

    private SSLProxy createSSLProxy(int port, SSLInterceptor... sslInterceptors) {
        SSLProxy sslProxy = new SSLProxy();
        sslProxy.setHost(SNI_HOST);
        sslProxy.setPort(port);
        sslProxy.setUseAsDefault(false);
        sslProxy.setSslInterceptors(List.of(sslInterceptors));
        SSLProxy.Target target = new SSLProxy.Target();
        target.setHost(backend.getInetAddress().getHostAddress());
        target.setPort(backend.getLocalPort());
        sslProxy.setTarget(target);
        return sslProxy;
    }

    private byte[] readFirstBytesFromBackend() throws IOException {
        try (Socket socket = backend.accept(); InputStream in = socket.getInputStream()) {
            byte[] buffer = new byte[0x1000];
            int read = in.read(buffer);
            byte[] result = new byte[read];
            System.arraycopy(buffer, 0, result, 0, read);
            return result;
        }
    }

    /**
     * Starts a real TLS handshake and lets it fail: the target of this test is a plain socket,
     * so no ServerHello ever comes back. Only the ClientHello - including its SNI - matters here.
     *
     * @return the error the handshake failed with
     */
    private IOException sendClientHello(int port, String sniHost) throws IOException {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(InetAddress.getLoopbackAddress(), port)) {
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setServerNames(List.of(new SNIHostName(sniHost)));
            socket.setSSLParameters(parameters);
            socket.setSoTimeout(1000);
            socket.startHandshake();
        } catch (IOException e) {
            return e; // the handshake cannot complete, see javadoc
        }
        return null;
    }
}
