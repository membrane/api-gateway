/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import com.predic8.membrane.core.http.BodyLengthCheck.TooLong;

import static com.predic8.membrane.core.http.BodyLengthCheck.WITHIN;
import static com.google.common.io.Resources.getResource;
import static com.predic8.membrane.annot.Constants.CRLF;
import static com.predic8.membrane.core.http.ChunksBuilder.chunks;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.HTTP2_SERVER;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

public class ChunkedBodyTest {

    private static final ObjectMapper om = new ObjectMapper();

    @Test
    void testReadTrailer() throws Exception {
        try (ServerSocket ss = new ServerSocket(3058)) {
            Thread t = new Thread(() -> {
                try {
                    try (Socket s = ss.accept()) {
                        URL resource = getResource("chunked-response-with-trailer.txt");
                        String cont = Resources.toString(resource, US_ASCII);
                        cont = cont.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\\\\n", "\n").replaceAll("\\\\r", "\r");
                        s.getOutputStream().write(cont.getBytes(US_ASCII));
                        s.getOutputStream().flush();
                        try {
                            sleep(100); // a TCP close may outpace the TCP data paket elsewise
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            t.start();

            Exchange e = get("http://localhost:3058").buildExchange();
            try (var hc = new HttpClient()) {
                hc.call(e);
            }
            e.getResponse().getBodyAsStringDecoded(); // read body
            assertEquals("Mon, 12 Dec 2022 09:28:00 GMT", e.getResponse().getBody().getTrailer().getFirstValue("Expires"));
            t.interrupt();
        }
    }

    @Test
    void testWriteTrailer() throws IOException {
        Router router = setupRouter(false, false);
        try {
            org.apache.commons.httpclient.HttpClient hc = new org.apache.commons.httpclient.HttpClient();
            for (int i = 0; i < 2; i++) {
                GetMethod req = new GetMethod("http://localhost:3059");
                hc.executeMethod(req);

                assertEquals("predic8DeveloperNetwork", req.getResponseBodyAsString());
                assertEquals("Mon, 12 Dec 2022 09:28:00 GMT", req.getResponseFooter("Expires").getValue());
            }
        } finally {
            router.stop();
        }
    }

    @Test
    void readWriteTrailerHttp2() throws IOException {
        Router router = setupRouter(false, true);
        Router router2 = setupRouter(true, false);
        try {
            org.apache.commons.httpclient.HttpClient hc = new org.apache.commons.httpclient.HttpClient();
            for (int i = 0; i < 2; i++) {
                GetMethod req = new GetMethod("http://localhost:3059");
                hc.executeMethod(req);

                assertEquals("predic8DeveloperNetwork", req.getResponseBodyAsString());
                assertEquals("Mon, 12 Dec 2022 09:28:00 GMT", req.getResponseFooter("Expires").getValue());
            }
        } finally {
            router2.stop();
            router.stop();
        }
    }

    @Test
    void testWriteTrailerHttp2() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        Router router = setupRouter(true, false);
        try {
            X509TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            };
            TrustManager[] trustAllCerts = new TrustManager[]{trustAll};


            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            OkHttpClient hc = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAll)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
            for (int i = 0; i < 2; i++) {
                Call call = hc.newCall(new okhttp3.Request.Builder().url("https://localhost:3060").build());
                try (okhttp3.Response res = call.execute()) {
                    assertEquals("predic8DeveloperNetwork", res.body().string());
                    assertEquals("Mon, 12 Dec 2022 09:28:00 GMT", res.trailers().get("Expires"));
                }
            }
            try (ExecutorService es = hc.dispatcher().executorService()) {
                es.shutdown();
            }
            hc.connectionPool().evictAll();
        } finally {
            router.stop();
        }
    }

    private Router setupRouter(boolean http2, boolean http2Client) throws IOException {
        TestRouter router = new TestRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(http2 ? 3060 : 3059), "localhost", 3060);
        if (http2) {
            SSLParser sslParser = getSslParserForHttp2();
            sp.setSslInboundParser(sslParser);
        }
        AtomicReference<String> remoteSocketAddr = new AtomicReference<>();
        if (http2Client) {
//            HTTPClientInterceptor interceptor = (HTTPClientInterceptor) router.getTransport().getFlow().stream().filter(i -> i instanceof HTTPClientInterceptor).findFirst().get();
//            HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();
//            httpClientConfig.setUseExperimentalHttp2(true);
//            interceptor.setHttpClientConfig(httpClientConfig);
            SSLParser sslParser2 = getSslParserForHttp2Client();
            sp.getTarget().setSslParser(sslParser2);
        } else {
            sp.getFlow().add(getInterceptor(http2, remoteSocketAddr));
        }
        router.add(sp);
        router.start();
        if (http2Client) {
            HTTPClientInterceptor interceptor = router.getTransport().getFirstInterceptorOfType(HTTPClientInterceptor.class).orElseThrow();
            HttpClientConfiguration cfg = new HttpClientConfiguration();
            cfg.setUseExperimentalHttp2(true);
            interceptor.updateHttpClientConfig(cfg);
        }
        return router;
    }

    private static @NotNull AbstractInterceptor getInterceptor(boolean http2, AtomicReference<String> remoteSocketAddr) {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                String remoteAddr = getRemoteAddr(exc);
                if (remoteSocketAddr.get() == null) {
                    remoteSocketAddr.set(remoteAddr);
                } else if (!remoteAddr.equals(remoteSocketAddr.get())) {
                    throw new RuntimeException("Keep-Alive is not working.");
                }

                if (http2 && exc.getProperty(HTTP2_SERVER) == null)
                    throw new RuntimeException("HTTP/2 is not being used.");
                if (!http2 && exc.getProperty(HTTP2_SERVER) != null)
                    throw new RuntimeException("HTTP/2 is being used.");

                Response r = Response.ok().build();
                r.getHeader().removeFields("Content-Length");
                r.getHeader().setValue("Transfer-Encoding", "chunked");
                r.getHeader().setValue("Content-Type", "text/plain");
                r.getHeader().setValue("Trailer", "Expires");
                r.setBody(new ChunkedBody(new ByteArrayInputStream(getContent())));
                exc.setResponse(r);
                return RETURN;
            }

            private static String getRemoteAddr(Exchange exc) {
                return ((HttpServerHandler) exc.getHandler()).getSourceSocket().getRemoteSocketAddress().toString();
            }

            private static byte[] getContent() {
                String cont;
                try {
                    cont = Resources.toString(getResource("chunked-body-with-trailer.txt"), US_ASCII);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                cont = cont.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\\\\n", "\n").replaceAll("\\\\r", "\r");
                return cont.getBytes(US_ASCII);
            }
        };
    }

    private static @NotNull SSLParser getSslParserForHttp2() {
        SSLParser sslParser = new SSLParser();
        sslParser.setUseExperimentalHttp2(true);
        sslParser.setEndpointIdentificationAlgorithm("");
        sslParser.setShowSSLExceptions(true);
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");
        return sslParser;
    }

    private static @NotNull SSLParser getSslParserForHttp2Client() {
        SSLParser sslParser2 = new SSLParser();
        sslParser2.setEndpointIdentificationAlgorithm("");
        sslParser2.setShowSSLExceptions(true);
        sslParser2.setUseExperimentalHttp2(true);
        sslParser2.setTrustStore(new TrustStore());
        sslParser2.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
        sslParser2.getTrustStore().setPassword("secret");
        return sslParser2;
    }

    @Test
    void oneLine() throws IOException {

        byte[] bytes = chunks().add("Encircles the cell, the very heart.").build();

        ChunkedBody cb = new ChunkedBody(new ByteArrayInputStream(bytes));
        cb.read();

        assertEquals(1, cb.chunks.size());
    }

    @Test
    void getRawOfEmptyBody() {
        var cb = new ChunkedBody(new ByteArrayInputStream(("0" + CRLF + CRLF).getBytes(US_ASCII)));
        cb.read();
        assertEquals("0" + CRLF + CRLF, new String(cb.getRaw(), US_ASCII));
    }

    @Test
    void getRawOfBodyWithChunks() {
        var raw = "3" + CRLF + "abc" + CRLF + "10" + CRLF + "0123456789abcdef" + CRLF + "0" + CRLF + CRLF;
        var cb = new ChunkedBody(new ByteArrayInputStream(raw.getBytes(US_ASCII)));
        cb.read();
        assertEquals(raw, new String(cb.getRaw(), US_ASCII));
    }

    @Test
    void readStream() throws IOException {
        ByteArrayInputStream bis = getJSONBodyWithSingleChunk();
        ChunkedBody cb = new ChunkedBody(bis);
        InputStream is = cb.getContentAsStream();

        // Read the complete JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());

        // But 0 + CRLF + CRLF is still not read from stream
        assertEquals(5, bis.available());

        // No data is available that means no more chunks, but the input stream is still not read completely
        assertEquals(0, is.available());

        //  0 + CRLF + CRLF is not read yet
        assertFalse(cb.isRead());

        if (!(is instanceof BodyInputStream bodyIs)) {
            fail();
            return;
        }

        // Try to read next chunk which does not exist. Now chunk trailer should be read
        assertNull(bodyIs.readNextChunk());

        // Message is now completely read
        assertTrue(cb.isRead());
    }

    @Test
    void readStreamRepeatedly() throws IOException {
        ByteArrayInputStream bis = getJSONBodyWithSingleChunk();
        ChunkedBody cb = new ChunkedBody(bis);

        // Read the JSON from the body
        assertEquals(42, om.readTree(cb.getContentAsStream()).get("foo").asInt());

        // Re-Read JSON from the body (both reads will leave the final end-chunk unread)
        assertEquals(42, om.readTree(cb.getContentAsStream()).get("foo").asInt());

        if (!(cb.getContentAsStream() instanceof BodyInputStream bodyIs)) {
            fail();
            return;
        }

        // Try to read next chunk which does not exist. Now chunk trailer should be read
        assertNull(bodyIs.readNextChunk());

        // Message is now completely read
        assertTrue(cb.isRead());
    }

    private static @NotNull ByteArrayInputStream getJSONBodyWithSingleChunk() {
        return new ByteArrayInputStream(chunks().add("""
                { "foo": 42 }""").build());
    }

    @Test
    void readStreamDiscard() throws IOException {
        ByteArrayInputStream bis = getJSONBodyWithSingleChunk();
        ChunkedBody cb = new ChunkedBody(bis);

        InputStream is = cb.getContentAsStream();

        // Read the complete JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());

        cb.discard();

        // discard() should read all the bytes
        assertEquals(0, bis.available());
    }

    @Test
    void contentOfADiscardedBodyCannotBeRead() {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("first").add("second").build()));

        body.discard();

        assertThrows(IllegalStateException.class, body::getContent);
        assertThrows(IllegalStateException.class, body::getContentAsStream);
        assertEquals(0, body.getLength());
    }

    /**
     * The chunks read before the discard are still in memory, but they are only the start of the body.
     */
    @Test
    void contentOfABodyDiscardedAfterAPartialReadCannotBeRead() throws IOException {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("first").add("second").build()));
        assertEquals('f', body.getContentAsStream().read());

        body.discard();

        assertThrows(IllegalStateException.class, body::getContent);
        assertThrows(IllegalStateException.class, body::getContentAsStream);
        assertEquals("first".length(), body.getLength());
    }

    /**
     * Writing a body discarded after a partial read would send only its start as if it were complete.
     */
    @Test
    void bodyDiscardedAfterAPartialReadCannotBeWritten() throws IOException {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("first").add("second").build()));
        assertEquals('f', body.getContentAsStream().read());
        body.discard();
        final var out = new ByteArrayOutputStream();

        assertThrows(IllegalStateException.class, () -> body.write(new ChunkedBodyTransferer(out), false));
        assertThrows(IllegalStateException.class, () -> body.write(new ChunkedBodyTransferer(out), true));
        assertEquals(0, out.size());
    }

    @Test
    void readUpToReadsAChunkedBodyWithinTheLimit() {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("sma").add("ll").build()));
        assertEquals(WITHIN, body.readUpTo(5));
        assertTrue(body.isRead());
        assertArrayEquals("small".getBytes(US_ASCII), body.getContent());
    }

    @Test
    void readUpToStopsAtTheSizeOfTheChunkThatExceedsTheLimit() {
        final var in = new ByteArrayInputStream(chunks().add("first").add("second").add("third").build());
        final var body = new ChunkedBody(in);
        assertEquals(new TooLong(true), body.readUpTo(8));
        assertTrue(body.hasFailed());
        assertEquals(("second" + CRLF + "5" + CRLF + "third" + CRLF + "0" + CRLF + CRLF).length(), in.available());
    }

    /**
     * A sender cannot make the gateway allocate a large chunk, nor wait for its data, by announcing it.
     */
    @Test
    void readUpToRejectsAChunkLongerThanTheLimitBeforeReadingIt() {
        final var in = new ByteArrayInputStream(("6400000" + CRLF).getBytes(US_ASCII));
        final var body = new ChunkedBody(in);
        assertEquals(new TooLong(true), body.readUpTo(1024));
        assertTrue(body.hasFailed());
    }

    @Test
    void readUpToCountsAPartialReadThatAlreadyExceedsTheLimit() throws IOException {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("first").build()));
        assertEquals('f', body.getContentAsStream().read());

        assertEquals(new TooLong(true), body.readUpTo(3));
        assertTrue(body.hasFailed());
    }

    /**
     * The chunks read before belong to the body: they count, and they are kept.
     */
    @Test
    void readUpToContinuesAPartialRead() throws IOException {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("first").add("second").build()));
        assertEquals('f', body.getContentAsStream().read());

        assertEquals(WITHIN, body.readUpTo(11));
        assertArrayEquals("firstsecond".getBytes(US_ASCII), body.getContent());
    }

    @Test
    void readUpToCountsTheChunksOfAPartialRead() throws IOException {
        final var body = new ChunkedBody(new ByteArrayInputStream(chunks().add("first").add("second").build()));
        assertEquals('f', body.getContentAsStream().read());

        assertEquals(new TooLong(true), body.readUpTo(10));
        assertTrue(body.hasFailed());
    }

    @Test
    void readStreamWith2ConcatenatedJSON() throws IOException {
        ByteArrayInputStream bis = createBodyWith2ConcatenatedJSONs();
        ChunkedBody cb = new ChunkedBody(bis);
        InputStream is = cb.getContentAsStream();

        // Read the first JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());

        // Read the second JSON from the body
        assertEquals(43, om.readTree(is).get("foo").asInt());

        if (!(is instanceof BodyInputStream bodyIs)) {
            fail();
            return;
        }

        // Try to read next chunk which does not exist. Now chunk trailer should be read
        assertNull(bodyIs.readNextChunk());

        // Message is now completely read
        assertTrue(cb.isRead());
    }

    @Test
    void readHalfStreamAndThenTheRest() throws IOException {
        ByteArrayInputStream bis = createBodyWith2ConcatenatedJSONs();
        ChunkedBody cb = new ChunkedBody(bis);
        InputStream is = cb.getContentAsStream();

        // Read the first JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());

        // now switch to read() which reads everything which is left
        cb.read();

        // now assert that everything is actually there
        assertEquals(26, cb.getContent().length);
    }

    @Test
    void readHalfStreamAndThenGetLength() throws IOException {
        ByteArrayInputStream bis = createBodyWith2ConcatenatedJSONs();
        ChunkedBody cb = new ChunkedBody(bis);
        InputStream is = cb.getContentAsStream();

        // Read the first JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());

        // now assert that everything is actually there
        assertEquals(26, cb.getLength());
    }

    @Test
    void readEverythingAndThenStream() throws IOException {
        ByteArrayInputStream bis = createBodyWith2ConcatenatedJSONs();
        ChunkedBody cb = new ChunkedBody(bis);

        // read() everything
        cb.read();

        // assert that everything is actually there
        assertEquals(26, cb.getContent().length);

        InputStream is = cb.getContentAsStream();
        // Read the first JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());
        // Read the second JSON from the body
        assertEquals(43, om.readTree(is).get("foo").asInt());
        // now the end of stream has been reached
        assertEquals(-1, is.read());
    }

    private static @NotNull ByteArrayInputStream createBodyWith2ConcatenatedJSONs() {
        return new ByteArrayInputStream(chunks().add("""
                { "foo": 42 }""").add("""
                { "foo": 43 }""").build());
    }

    // -------------------------------------------------------------------------
    // truncated bodies (https://github.com/membrane/api-gateway/issues/3193)
    // -------------------------------------------------------------------------

    /**
     * A sender that announces a 7-byte chunk and then disappears. readByteArray() pads the
     * shortfall with NULs, so the unfinished chunk is handed on as if it were complete and the
     * failure surfaces only on the next chunk-size line, as "Empty chunk-size field".
     */
    @Nested
    class TruncatedChunk {

        private static final String TRUNCATED = "7" + CRLF + "part";

        private static ChunkedBody truncatedBody() {
            return new ChunkedBody(new ByteArrayInputStream(TRUNCATED.getBytes()));
        }

        @Test
        void isNotDeliveredToTheConsumer() {
            InputStream in = truncatedBody().getContentAsStream();

            assertThrows(IOException.class, () -> in.readNBytes(7));
        }

        @Test
        void isNotPublishedToObservers() {
            ChunkedBody body = truncatedBody();
            List<byte[]> published = new ArrayList<>();
            body.addObserver(new AbstractMessageObserver() {
                @Override
                public void bodyChunk(Chunk chunk) {
                    published.add(chunk.content());
                }
            });

            InputStream in = body.getContentAsStream();
            assertThrows(IOException.class, in::readAllBytes);

            assertEquals(0, published.size(), "a chunk the sender never finished must not reach an observer");
        }

        @Test
        void failsWithEndOfStreamNotAnEmptyChunkSize() {
            ReadingBodyException e = assertThrows(ReadingBodyException.class, truncatedBody()::read);

            assertInstanceOf(EOFException.class, e.getCause());
        }

        @Test
        void isNotForwardedDownstream() {
            ChunkedBody body = truncatedBody();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            assertThrows(ReadingBodyException.class, () -> body.write(new ChunkedBodyTransferer(out), false));

            assertEquals(0, out.size(), "no part of an unfinished chunk may reach the peer");
        }
    }

}
