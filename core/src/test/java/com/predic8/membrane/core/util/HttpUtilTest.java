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

import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.net.*;
import java.net.URI;
import java.util.List;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
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
	public void testReadLine() throws IOException, EndOfStreamException {
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
		assertEquals("<html><head><title>caption</title></head><body><h1>caption</h1><p>body</p></body></html>", unescapedHtmlMessage("caption","body"));
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

    @Test
    void getPortTests() throws URISyntaxException, MalformedURLException {
		assertEquals(80, getPort("http://localhost"));
		assertEquals(8080, getPort("http://localhost:8080"));
		assertEquals(443, getPort("https://localhost"));
		assertEquals(8443, getPort("https://localhost:8443"));
    }

	private static int getPort(String uri) throws MalformedURLException, URISyntaxException {
		return HttpUtil.getPort(new URI(uri).toURL());
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
}
