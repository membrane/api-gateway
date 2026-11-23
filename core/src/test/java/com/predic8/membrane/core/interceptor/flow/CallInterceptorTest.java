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
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxyKey;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.flow.CallInterceptor.copyHeaders;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePorts;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

class CallInterceptorTest {

    private static final String CALLOUT_BODY = "{\"items\":[%s\"end\"]}".formatted("\"abcdefghij\",".repeat(50));

    private Exchange exc;
    private Router router;

    @BeforeEach
    void setup() throws URISyntaxException {
        exc = get("/foo").buildExchange();
        exc.setProperty("a", "b");
        router = new DefaultRouter();
    }

    @AfterEach
    void teardown() {
        router.stop();
    }

    @Test
    void filterHeaders() {
        exc.setResponse(Response.ok()
                .header(TRANSFER_ENCODING, "foo")
                .header(CONTENT_ENCODING, "bar")
                .header(SERVER, "dummy")
                .header("X-FOO", "42").build());

        copyHeaders(exc.getResponse(), exc.getRequest());

        // preserve
        var header = exc.getRequest().getHeader();
        assertEquals("42", header.getFirstValue("X-FOO"));

        // take out
        assertNull(header.getFirstValue(SERVER));
        assertNull(header.getFirstValue(TRANSFER_ENCODING));
        assertNull(header.getFirstValue(CONTENT_ENCODING));
    }

    /**
     * Most servers answer with <code>Connection: keep-alive</code>. That describes the callout's own connection and
     * must not replace the client's <code>Connection: close</code>.
     */
    @Test
    void calloutKeepAliveLeavesRequestConnectionUnchanged() throws URISyntaxException {
        final var original = get("/foo").header(CONNECTION, CLOSE).buildExchange();

        copyHeaders(Response.ok()
                .header(CONNECTION, "keep-alive")
                .header(KEEP_ALIVE, "timeout=5, max=100")
                .build(), original.getRequest());

        final var request = original.getRequest();
        assertEquals(CLOSE, request.getHeader().getConnection());
        assertNull(request.getHeader().getFirstValue(KEEP_ALIVE));
        assertFalse(request.isKeepAlive());
    }

    @Test
    void calloutConnectionCloseLeavesRequestKeepAlive() throws URISyntaxException {
        final var original = get("/foo").buildExchange();

        copyHeaders(Response.ok().header(CONNECTION, CLOSE).build(), original.getRequest());

        assertNull(original.getRequest().getHeader().getConnection());
        assertTrue(original.getRequest().isKeepAlive());
    }

    /**
     * RFC 9110 section 7.6.1: the fixed hop-by-hop fields and every field named in <code>Connection</code>.
     */
    @Test
    void hopByHopHeadersAreNotCopied() throws URISyntaxException {
        final var original = get("/foo").buildExchange();

        copyHeaders(Response.ok()
                .header(CONNECTION, "Upgrade, x-hop")
                .header(KEEP_ALIVE, "timeout=5")
                .header(PROXY_CONNECTION, "keep-alive")
                .header("TE", "trailers")
                .header("Trailer", "X-Checksum")
                .header(UPGRADE, "h2,h2c")
                .header("X-Hop", "nominated by Connection")
                .header("X-End-To-End", "42")
                .build(), original.getRequest());

        final var header = original.getRequest().getHeader();
        assertEquals("42", header.getFirstValue("X-End-To-End"));
        assertAll(Stream.of(CONNECTION, KEEP_ALIVE, PROXY_CONNECTION, "TE", "Trailer", UPGRADE, "X-Hop")
                .map(name -> () -> assertNull(header.getFirstValue(name), name)));
    }

    @Test
    void clientHopByHopHeadersAreNotSentToCallout() throws Exception {
        final var received = new AtomicReference<ReceivedRequest>();
        final var port = freePort();
        router.add(recordingCallout(port, received));
        router.start();
        final var exchange = get("/foo")
                .header(CONNECTION, "close")
                .header(KEEP_ALIVE, "timeout=5")
                .header("TE", "trailers")
                .header("Trailer", "X-Checksum")
                .header("X-End-To-End", "42")
                .buildExchange();

        call("GET", "http://localhost:" + port + "/").handleRequest(exchange);

        final var header = received.get().header();
        assertEquals("42", header.getFirstValue("X-End-To-End"));
        assertAll(Stream.of(CONNECTION, KEEP_ALIVE, "TE", "Trailer")
                .map(name -> () -> assertNull(header.getFirstValue(name), name)));
    }

    /**
     * By the time <code>call</code> runs, the gateway has added fields of its own. A client must not be able to
     * remove them from the callout by naming them in <code>Connection</code>.
     */
    @Test
    void connectionCannotRemoveFieldsAddedByTheGateway() throws Exception {
        final var received = new AtomicReference<ReceivedRequest>();
        final var port = freePort();
        router.add(recordingCallout(port, received));
        router.start();
        final var exchange = get("/foo")
                .header(CONNECTION, "X-Forwarded-For, Authorization")
                .header(X_FORWARDED_FOR, "192.0.2.1")
                .header(AUTHORIZATION, "Bearer gateway-token")
                .buildExchange();

        call("GET", "http://localhost:" + port + "/").handleRequest(exchange);

        final var header = received.get().header();
        // The callout endpoint appends the address of its own client
        assertEquals("192.0.2.1, 127.0.0.1", header.getFirstValue(X_FORWARDED_FOR));
        assertEquals("Bearer gateway-token", header.getFirstValue(AUTHORIZATION));
        assertNull(header.getFirstValue(CONNECTION));
    }

    /**
     * The client's upgrade request is about its connection to the gateway, not about the callout.
     */
    @Test
    void clientUpgradeRequestDoesNotBreakCallout() throws Exception {
        final var port = freePort();
        router.add(calloutEndpoint(port, () -> Response.ok("callout").build()));
        router.start();
        final var exchange = get("/foo").header(CONNECTION, UPGRADE).header(UPGRADE, "websocket").buildExchange();

        assertEquals(CONTINUE, call("GET", "http://localhost:" + port + "/").handleRequest(exchange));
        assertEquals("callout", exchange.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void calloutReceivesDecodedBody() throws Exception {
        final var received = new AtomicReference<ReceivedRequest>();
        final var port = freePort();
        router.add(recordingCallout(port, received));
        router.start();
        // body() removes Content-Encoding, so it has to come first
        final var exchange = post("/foo")
                .body(gzip("{\"name\":\"Apricot\"}"))
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(CONTENT_ENCODING, "gzip")
                .buildExchange();

        call("POST", "http://localhost:" + port + "/").handleRequest(exchange);

        assertNull(received.get().header().getFirstValue(CONTENT_ENCODING));
        assertEquals("{\"name\":\"Apricot\"}", received.get().body());
    }

    /**
     * Membrane does not decode <code>compress</code>, so the body is sent as it is, together with its coding.
     */
    @Test
    void calloutReceivesUndecodedBodyWithItsCoding() throws Exception {
        final var received = new AtomicReference<ReceivedRequest>();
        final var port = freePort();
        router.add(recordingCallout(port, received));
        router.start();
        // body() removes Content-Encoding, so it has to come first
        final var exchange = post("/foo")
                .body("compressed")
                .header(CONTENT_ENCODING, "compress")
                .buildExchange();

        call("POST", "http://localhost:" + port + "/").handleRequest(exchange);

        assertEquals("compress", received.get().header().getFirstValue(CONTENT_ENCODING));
        assertEquals("compressed", received.get().body());
    }

    /**
     * A GET is sent without a body, so nothing may describe one.
     */
    @Test
    void bodylessCalloutCarriesNoContentEncoding() throws Exception {
        final var received = new AtomicReference<ReceivedRequest>();
        final var port = freePort();
        router.add(recordingCallout(port, received));
        router.start();
        // body() removes Content-Encoding, so it has to come first
        final var exchange = post("/foo")
                .body(gzip("{\"name\":\"Apricot\"}"))
                .header(CONTENT_ENCODING, "gzip")
                .buildExchange();

        call("GET", "http://localhost:" + port + "/").handleRequest(exchange);

        assertNull(received.get().header().getFirstValue(CONTENT_ENCODING));
        assertEquals("", received.get().body());
    }

    /**
     * Membrane does not decode <code>compress</code>, so the endpoint's body is set as it is, together with its coding.
     */
    @Test
    void undecodedCalloutBodyKeepsItsCoding() throws Exception {
        final var port = freePort();
        // body() removes Content-Encoding, so it has to come first
        router.add(calloutEndpoint(port, () -> Response.ok().body("compressed").header(CONTENT_ENCODING, "compress").build()));
        router.start();

        call("GET", "http://localhost:" + port + "/").handleRequest(exc);

        final var request = exc.getRequest();
        assertEquals("compress", request.getHeader().getFirstValue(CONTENT_ENCODING));
        assertEquals("compressed", new String(request.getBody().getContent(), UTF_8));
        assertEquals("compressed".length(), request.getHeader().getContentLength());
    }

    @Test
    void responseFlowMergesCalloutIntoResponse() throws Exception {
        final var port = freePort();
        router.add(calloutEndpoint(port, () -> Response.ok("callout").header("X-Callout", "1").build()));
        router.start();
        exc.setResponse(Response.ok("backend").build());

        call("GET", "http://localhost:" + port + "/").handleResponse(exc);

        assertEquals("callout", exc.getResponse().getBodyAsStringDecoded());
        assertEquals("1", exc.getResponse().getHeader().getFirstValue("X-Callout"));
        assertEquals("", exc.getRequest().getBodyAsStringDecoded());
        assertNull(exc.getRequest().getHeader().getFirstValue("X-Callout"));
    }

    /**
     * In the response flow, the URL is evaluated against the response and the response body is sent.
     */
    @Test
    void responseFlowCallsWithResponse() throws Exception {
        final var received = new AtomicReference<ReceivedRequest>();
        final var port = freePort();
        router.add(recordingCallout(port, received));
        router.start();
        final var exchange = post("/foo").body("request body").header("X-Id", "request").buildExchange();
        exchange.setResponse(Response.ok("response body").header("X-Id", "response").build());

        call("POST", "http://localhost:" + port + "/${header['X-Id']}").handleResponse(exchange);

        assertEquals("/response", received.get().uri());
        assertEquals("response body", received.get().body());
    }

    @Test
    void evaluateUrlTemplate() throws IOException {
        extracted("Path: /b");
    }

    @Test
    void urlTemplateAndAllowIllegalCharactersInURL() {
        router.getConfiguration().getUriFactory().setAllowIllegalCharacters(true);
        assertThrows(ConfigurationException.class, () -> extracted("dummy"));
    }

    @Test
    void usesRouterHttpClientConfig() throws Exception {
        int proxyPort = freePort();
        var proxyApi = new APIProxy();
        proxyApi.setKey(new APIProxyKey(proxyPort));
        proxyApi.getFlow().add(new TemplateInterceptor() {{
            setSrc("via proxy");
        }});
        proxyApi.getFlow().add(new ReturnInterceptor());
        router.add(proxyApi);

        var proxy = new ProxyConfiguration();
        proxy.setHost("localhost");
        proxy.setPort(proxyPort);
        var httpClientConfig = new HttpClientConfiguration();
        httpClientConfig.setProxy(proxy);
        router.getConfiguration().setHttpClientConfig(httpClientConfig);
        router.start();

        var ci = new CallInterceptor();
        ci.setUrl("http://example.com/foo");
        ci.init(router);
        ci.handleRequest(exc);
        assertEquals("via proxy", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void elementHttpClientConfigOverridesRouterConfig() throws Exception {
        int proxyPort = freePort();
        var proxyApi = new APIProxy();
        proxyApi.setKey(new APIProxyKey(proxyPort));
        proxyApi.getFlow().add(new TemplateInterceptor() {{
            setSrc("via element proxy");
        }});
        proxyApi.getFlow().add(new ReturnInterceptor());
        router.add(proxyApi);
        router.start();

        var proxy = new ProxyConfiguration();
        proxy.setHost("localhost");
        proxy.setPort(proxyPort);
        var httpClientConfig = new HttpClientConfiguration();
        httpClientConfig.setProxy(proxy);

        var ci = new CallInterceptor();
        ci.setUrl("http://example.com/foo");
        ci.setHttpClientConfig(httpClientConfig);
        ci.init(router);
        ci.handleRequest(exc);
        assertEquals("via element proxy", exc.getRequest().getBodyAsStringDecoded());
    }

    /**
     * A callout server that closes its connection must not close the connection between client and gateway.
     */
    @Test
    void clientConnectionStaysOpenWhenCalloutCloses() throws IOException {
        final var ports = freePorts(2);
        router.add(calloutEndpoint(ports[0], () -> Response.ok("callout").header(CONNECTION, CLOSE).build()));
        final var gateway = gateway(ports[1], "http://localhost:" + ports[0] + "/");
        gateway.getFlow().add(new ReturnInterceptor());
        router.add(gateway);
        router.start();

        given()
            .get("http://localhost:" + ports[1] + "/foo")
        .then()
            .statusCode(200)
            .header(CONNECTION, not(equalToIgnoringCase(CLOSE)));
    }

    /**
     * A callout server that offers an upgrade to HTTP/2 must not turn the request forwarded to the target into an
     * upgrade request.
     */
    @Test
    void calloutUpgradeOfferDoesNotBreakForwarding() throws IOException {
        final var ports = freePorts(3);
        router.add(calloutEndpoint(ports[0], () -> Response.ok("callout")
                .header(CONNECTION, "Upgrade, Keep-Alive")
                .header(UPGRADE, "h2,h2c")
                .build()));
        router.add(backend(ports[1]));
        final var gateway = gateway(ports[2], "http://localhost:" + ports[0] + "/");
        gateway.setTarget(new Target("localhost", ports[1]));
        router.add(gateway);
        router.start();

        given()
            .get("http://localhost:" + ports[2] + "/foo")
        .then()
            .statusCode(200)
            .body(equalTo("backend"));
    }

    @Test
    void contentLengthDescribesDecodedCalloutBody() throws Exception {
        final var port = freePort();
        router.add(calloutEndpoint(port, CallInterceptorTest::gzipCalloutResponse));
        router.start();

        final var call = new CallInterceptor();
        call.setUrl("http://localhost:" + port + "/");
        call.init(router);
        call.handleRequest(exc);

        final var request = exc.getRequest();
        assertEquals(CALLOUT_BODY, request.getBodyAsStringDecoded());
        assertEquals(CALLOUT_BODY.getBytes(UTF_8).length, request.getHeader().getContentLength());
    }

    @Test
    void targetReceivesWholeDecodedCalloutBody() throws IOException {
        final var ports = freePorts(3);
        router.add(calloutEndpoint(ports[0], CallInterceptorTest::gzipCalloutResponse));
        router.add(echo(ports[1]));
        final var gateway = gateway(ports[2], "http://localhost:" + ports[0] + "/");
        gateway.setTarget(new Target("localhost", ports[1]));
        router.add(gateway);
        router.start();

        given()
            .get("http://localhost:" + ports[2] + "/foo")
        .then()
            .statusCode(200)
            .body(equalTo(CALLOUT_BODY));
    }

    /**
     * The server the call interceptor calls. Answers every request with a fresh response from the supplier.
     */
    private static APIProxy calloutEndpoint(final int port, final Supplier<Response> response) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(response.get());
                return RETURN;
            }
        });
        return api;
    }

    /**
     * The request the callout endpoint received, copied when it arrived.
     */
    private record ReceivedRequest(String uri, Header header, String body) {}

    private static APIProxy recordingCallout(final int port, final AtomicReference<ReceivedRequest> received) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                final var request = exc.getRequest();
                received.set(new ReceivedRequest(request.getUri(), new Header(request.getHeader()),
                        new String(request.getBody().getContent(), UTF_8)));
                exc.setResponse(Response.ok("callout").build());
                return RETURN;
            }
        });
        return api;
    }

    private CallInterceptor call(final String method, final String url) {
        final var call = new CallInterceptor();
        call.setMethod(method);
        call.setUrl(url);
        call.init(router);
        return call;
    }

    /**
     * Calls in the request flow only, like the tutorials do.
     */
    private static APIProxy gateway(final int port, final String calloutUrl) {
        final var call = new CallInterceptor();
        call.setUrl(calloutUrl);
        final var request = new RequestInterceptor();
        request.setFlow(List.of(call));
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(request);
        return api;
    }

    private static APIProxy backend(final int port) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(new TemplateInterceptor() {{
            setSrc("backend");
        }});
        api.getFlow().add(new ReturnInterceptor());
        return api;
    }

    /**
     * Returns the request body it received.
     */
    private static APIProxy echo(final int port) {
        final var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(new ReturnInterceptor());
        return api;
    }

    /**
     * The compressed body with its Content-Length, as a server answers a request that accepts gzip.
     */
    private static Response gzipCalloutResponse() {
        // body() sets the Content-Length and removes Content-Encoding, so it has to come first
        return Response.ok()
                .body(gzip(CALLOUT_BODY))
                .contentType(APPLICATION_JSON)
                .header(CONTENT_ENCODING, "gzip")
                .build();
    }

    private static byte[] gzip(final String content) {
        final var bytes = new ByteArrayOutputStream();
        try (final var out = new GZIPOutputStream(bytes)) {
            out.write(content.getBytes(UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private void extracted(String expected) throws IOException {
        int port = freePort();
        var api = new APIProxy();
        api.setKey(new APIProxyKey(port));
        api.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                System.out.println(exc);
                return super.handleRequest(exc);
            }
        });
        api.getFlow().add(new TemplateInterceptor() {{
            setSrc("Path: ${path}");
        }});
        api.getFlow().add(new ReturnInterceptor());
        router.add(api);
        router.start();

        var ci = new CallInterceptor();
        ci.setUrl("http://localhost:" + port + "/${property.a}");
        ci.init(router);
        ci.handleRequest(exc);
        assertEquals(expected, exc.getRequest().getBodyAsStringDecoded());
    }
}