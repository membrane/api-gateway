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

import com.fasterxml.jackson.databind.*;
import com.google.common.io.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import okhttp3.*;
import org.apache.commons.httpclient.methods.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.google.common.io.Resources.*;
import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.ChunkedBody.*;
import static com.predic8.membrane.core.http.ChunksBuilder.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class ChunkedBodyTest {

    private static final ObjectMapper om = new ObjectMapper();

    @Test
    void testReadChunkSize() throws Exception {
        assertEquals(15663, readChunkSize(new ByteArrayInputStream(("3d2F" + CRLF).getBytes())));
    }

    @Test
    void testReadChunkSizeWithExtension() throws Exception {
        String s = "3d2F" + CRLF + ";gfgfgfg" + CRLF;
        assertEquals(15663, readChunkSize(new ByteArrayInputStream(s.getBytes())));
    }

    @Test
    void testReadChunkSizeWithExtensionValue() throws Exception {
        String s = "3d2F" + CRLF + ";gfgf=gfg" + CRLF;
        assertEquals(15663, readChunkSize(new ByteArrayInputStream(s.getBytes())));
    }

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
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            t.start();

            Exchange e;
            try (HttpClient hc = new HttpClient()) {
                e = hc.call(new Request.Builder().get("http://localhost:3058").buildExchange());
            }
            e.getResponse().getBodyAsStringDecoded(); // read body

            assertEquals("Mon, 12 Dec 2022 09:28:00 GMT", e.getResponse().getBody().getTrailer().getFirstValue("Expires"));
        }
    }


    @Test
    void testWriteTrailer() throws IOException {
        HttpRouter router = setupRouter(false, false);
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
    void testReadWriteTrailerHttp2() throws IOException {
        HttpRouter router = setupRouter(false, true);
        HttpRouter router2 = setupRouter(true, false);
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
        HttpRouter router = setupRouter(true, false);
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
            try(ExecutorService es = hc.dispatcher().executorService()) {
                es.shutdown();
            }
            hc.connectionPool().evictAll();
        } finally {
            router.stop();
        }
    }

    private HttpRouter setupRouter(boolean http2, boolean http2Client) {
        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(http2 ? 3060 : 3059), "localhost", 3060);
        if (http2) {
            SSLParser sslParser = getSslParserForHttp2();
            sp.setSslInboundParser(sslParser);
        }
        AtomicReference<String> remoteSocketAddr = new AtomicReference<>();
        if (http2Client) {
            HTTPClientInterceptor interceptor = (HTTPClientInterceptor) router.getTransport().getInterceptors().stream().filter(i -> i instanceof HTTPClientInterceptor).findFirst().get();
            HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();
            httpClientConfig.setUseExperimentalHttp2(true);
            interceptor.setHttpClientConfig(httpClientConfig);
            SSLParser sslParser2 = getSslParserForHttp2Client();
            sp.getTarget().setSslParser(sslParser2);
        } else {
            sp.getInterceptors().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exc) {
                    String remoteAddr = getRemoteAddr(exc);
                    if (remoteSocketAddr.get() == null) {
                        remoteSocketAddr.set(remoteAddr);
                    } else if (!remoteAddr.equals(remoteSocketAddr.get())) {
                        throw new RuntimeException("Keep-Alive is not working.");
                    }

                    if (http2 && exc.getProperty(HTTP2) == null)
                        throw new RuntimeException("HTTP/2 is not being used.");
                    if (!http2 && exc.getProperty(HTTP2) != null)
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
            });
        }
        router.getRules().add(sp);
        router.start();
        return router;
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
    void readTrailerTest() throws IOException {
        byte[] trailer = ("3D" + CRLF + CRLF).getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(trailer);
        assertEquals(61, readChunkSize(is)); // 3D = 61
        readTrailer(is);
        assertEquals( 0,is.available()); // Check that everything is read
    }

    @Test
    void readStream() throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(chunks().add("""
            { "foo": 42 }""").build());
        ChunkedBody cb = new ChunkedBody(bis);

        InputStream is = cb.getContentAsStream();

        // Read the complete JSON from the body
        assertEquals(42, om.readTree(is).get("foo").asInt());

        // But 0 + CRLF + CRLF is still not read from stream
        assertEquals(5, bis.available());

        // No data is available that means no more chunks, but the input stream is still not read completely
        assertEquals(0, is.available());

        //  0 + CRLF + CRLF is not read yet
        assertFalse(cb.read);

        if(!(is instanceof BodyInputStream bodyIs)) {
            fail();
            return;
        }

        // Try to read next chunk which does not exist. Now chunk trailer should be read
        assertNull(bodyIs.readNextChunk());

        // Message is now completely read
        assertTrue(cb.read);
    }
}
