/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.net.*;
import java.util.*;

import com.predic8.membrane.core.proxies.ProxyRule;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class HttpUtilTest {

    private static final String s1 = "foo" + CRLF + "bar" + CRLF + CRLF;
    private static InputStream is1;

    @BeforeEach
    public void setUp() {
        is1 = new ByteArrayInputStream(s1.getBytes());
    }

    @Test
    public void testReadLine() throws IOException {
        assertEquals("foo", readLine(is1));
        assertEquals("bar", readLine(is1));
        assertEquals("", readLine(is1));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    public void testReadLineMessage() throws Exception {
        assertEquals("POST /operation/call HTTP/1.1", readLine(getClass().getClassLoader().getResourceAsStream("request-post.msg")));
    }

    @Test
    void unescapedHtmlMessageTest() {
        assertEquals("<html><head><title>caption</title></head><body><h1>caption</h1><p>body</p></body></html>", unescapedHtmlMessage("caption", "body"));
    }

    @Test
    void getForwardedForSimpleTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "192.168.15.15").buildExchange();
        assertEquals(List.of("192.168.15.15"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForRemoveMembraneTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "192.168.15.15, 8.7.6.5, 4.4.5.5, ::1, 8.7.6.5").buildExchange();
        exc.setRemoteAddrIp("8.7.6.5");
        assertEquals(List.of("192.168.15.15", "8.7.6.5", "4.4.5.5", "::1"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForCompoundTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "10.10.10.10, 7.7.8.8, 192.168.15.15").buildExchange();
        assertEquals(List.of("10.10.10.10", "7.7.8.8", "192.168.15.15"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForWhitespaceTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "  192.168.15.15,     10.10.10.10       ").buildExchange();
        assertEquals(List.of("192.168.15.15", "10.10.10.10"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForV6Test() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "628c:1f76:efbf:bd62:9528:869d:2333:e115").buildExchange();
        assertEquals(List.of("628c:1f76:efbf:bd62:9528:869d:2333:e115"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForHostnameTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "abc.xyz").buildExchange();
        assertEquals(List.of("abc.xyz"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForCompoundMultiHeaderTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "::1,628c::e115")
                .header(X_FORWARDED_FOR, "10.10.10.10, 8.8.8.8").buildExchange();
        assertEquals(List.of("::1", "628c::e115", "10.10.10.10", "8.8.8.8"), getForwardedForList(exc));
    }

    @ParameterizedTest
    @CsvSource({
            "'http://localhost', '/'",
            "'http://localhost/', '/'",
            "'http://localhost?', '/?'",
            "'http://localhost?foo=bar', '/?foo=bar'",
            "'http://localhost:2000', '/'",
            "'http://localhost:2000/', '/'",
            "'http://localhost:2000?', '/?'",
            "'http://localhost:2000?foor=bar', '/?foor=bar'",
            "'http://localhost:2000/foor?bar', '/foor?bar'"
    })
    public void testGetPathAndQueryString(String url, String expected) throws MalformedURLException {
        assertEquals(expected, HttpUtil.getPathAndQueryString(url));
    }

    @ParameterizedTest
    @CsvSource({
            "GET,true",
            "PUT,true",
            "DELETE,true",
            "OPTIONS,true",
            "HEAD,true",
            "TRACE,true",
            "POST,false",
            "PATCH,false",
            "CONNECT,false"
    })
    void idempotent(String method, boolean expected) {
        assertEquals(expected, isIdempotent(method));
    }

    @ParameterizedTest
    @CsvSource({
            "same-origin, false",
            "none, false",
            "same-site, true",
            "cross-site, true",
            "SAME-ORIGIN, false"
    })
    void crossOriginBySecFetchSite(String secFetchSite, boolean crossOrigin) throws Exception {
        assertEquals(crossOrigin, isCrossOrigin(Request.post("/").header(SEC_FETCH_SITE, secFetchSite).buildExchange()));
    }

    @ParameterizedTest
    @CsvSource({
            "http://gateway.example.com:9000, false",
            "HTTP://Gateway.Example.com:9000, false",
            "https://gateway.example.com:9000, true",
            "https://attacker.example.com, true",
            "http://gateway.example.com, true",
            "null, true",
            "'http://exa mple.com', true"
    })
    void crossOriginByOrigin(String origin, boolean crossOrigin) throws Exception {
        assertEquals(crossOrigin, isCrossOrigin(Request.post("/")
                .header(HOST, "gateway.example.com:9000")
                .header(ORIGIN, origin).buildExchange()));
    }

    @ParameterizedTest
    @CsvSource({
            "https://gateway.example.com, false",
            "https://gateway.example.com:443, false",
            "http://gateway.example.com, true",
            "http://gateway.example.com:443, true",
            "https://gateway.example.com:8443, true"
    })
    void crossOriginOnTlsProxy(String origin, boolean crossOrigin) throws Exception {
        final var exc = Request.post("/")
                .header(HOST, "gateway.example.com")
                .header(ORIGIN, origin).buildExchange();
        exc.setProxy(new ProxyRule() {
            @Override
            public String getProtocol() {
                return "https";
            }
        });
        assertEquals(crossOrigin, isCrossOrigin(exc));
    }

    @ParameterizedTest
    @CsvSource({
            "https, https://gateway.example.com, false",
            "https, http://gateway.example.com, true",
            "http, https://gateway.example.com, true",
            "'https, http', https://gateway.example.com, false",
            "', https', https://gateway.example.com, false"
    })
    void crossOriginBehindTlsTerminatingProxy(String forwardedProto, String origin, boolean crossOrigin) throws Exception {
        assertEquals(crossOrigin, isCrossOrigin(Request.post("/")
                .header(HOST, "gateway.example.com")
                .header(X_FORWARDED_PROTO, forwardedProto)
                .header(ORIGIN, origin).buildExchange()));
    }

    @ParameterizedTest
    @CsvSource({
            "gateway.example.com, https://gateway.example.com, false",
            "'gateway.example.com, internal:9000', https://gateway.example.com, false",
            "gateway.example.com, https://internal:9000, true",
            "attacker.example.com, https://gateway.example.com, true"
    })
    void crossOriginBehindHostRewritingProxy(String forwardedHost, String origin, boolean crossOrigin) throws Exception {
        assertEquals(crossOrigin, isCrossOrigin(Request.post("/")
                .header(HOST, "internal:9000")
                .header(X_FORWARDED_HOST, forwardedHost)
                .header(X_FORWARDED_PROTO, "https")
                .header(ORIGIN, origin).buildExchange()));
    }

    @ParameterizedTest
    @CsvSource({
            "https, https://gateway.example.com, false",
            "https, http://gateway.example.com, true",
            "http, http://gateway.example.com, false"
    })
    void emptyForwardedProtoFallsBackToProxyProtocol(String protocol, String origin, boolean crossOrigin) throws Exception {
        final var exc = Request.post("/")
                .header(HOST, "gateway.example.com")
                .header(X_FORWARDED_PROTO, ",")
                .header(ORIGIN, origin).buildExchange();
        exc.setProxy(new ProxyRule() {
            @Override
            public String getProtocol() {
                return protocol;
            }
        });
        assertEquals(crossOrigin, isCrossOrigin(exc));
    }

    @ParameterizedTest
    @CsvSource({
            "http://gateway.example.com, false",
            "http://attacker.example.com, true"
    })
    void emptyForwardedHostFallsBackToHost(String origin, boolean crossOrigin) throws Exception {
        assertEquals(crossOrigin, isCrossOrigin(Request.post("/")
                .header(HOST, "gateway.example.com")
                .header(X_FORWARDED_HOST, ",")
                .header(ORIGIN, origin).buildExchange()));
    }

    @Test
    void plainHttpRequestDefaultsToPort80() throws Exception {
        assertFalse(isCrossOrigin(Request.post("/")
                .header(HOST, "gateway.example.com:80")
                .header(ORIGIN, "http://gateway.example.com").buildExchange()));
    }

    @Test
    void requestWithoutFetchMetadataOrOriginIsNotCrossOrigin() throws Exception {
        assertFalse(isCrossOrigin(Request.post("/").header(HOST, "gateway.example.com:9000").buildExchange()));
    }

    @Test
    void secFetchSiteWinsOverOrigin() throws Exception {
        assertFalse(isCrossOrigin(Request.post("/")
                .header(HOST, "gateway.example.com:9000")
                .header(ORIGIN, "https://attacker.example.com")
                .header(SEC_FETCH_SITE, "same-origin").buildExchange()));
    }
}
