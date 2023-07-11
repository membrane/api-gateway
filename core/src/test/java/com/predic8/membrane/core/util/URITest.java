/* Copyright 2014 predic8 GmbH, www.predic8.com

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

import static org.junit.jupiter.api.Assertions.*;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

public class URITest {

	@Test
	public void doit() {
		assertSame("http://predic8.de/?a=query");
		assertSame("http://predic8.de/#foo");
		assertSame("http://predic8.de/path/file");
		assertSame("http://predic8.de/path/file?a=query");
		assertSame("http://predic8.de/path/file#foo");
		assertSame("http://predic8.de/path/file?a=query#foo");
		assertSame("http://foo:bar@predic8.de/path/file?a=query#foo");
		assertSame("//predic8.de/path/file?a=query#foo");
		assertSame("/path/file?a=query#foo");
		assertSame("scheme:/path/file?a=query#foo");
		assertSame("path/file?a=query#foo");
		assertSame("scheme:path/file?a=query#foo", true); // considered 'opaque' by java.net.URI - we don't support that
		assertSame("file?a=query#foo", true); // opaque
		assertSame("scheme:file?a=query#foo", true); // opaque
		assertSame("?a=query#foo");
		assertSame("scheme:?a=query#foo", true); // opaque
	}

	@SuppressWarnings("UnnecessaryUnicodeEscape")
	@Test
	public void testEncoding() {
		assertSame("http://predic8.de/path/file?a=quer\u00E4y#foo");
		assertSame("http://predic8.de/path/file?a=quer%C3%A4y#foo%C3%A4");
		assertSame("http://predic8.de/path/fi\u00E4le?a=query#foo");
		assertSame("http://predic8.de/path/fi%C3%A4le?a=query#foo");
		assertSame("http://predic8.de/pa\u00E4th/file?a=query#foo");
		assertSame("http://predic8.de/pa%C3%A4th/file?a=query#foo");
		assertSame("http://predic8.d\u00E4e/path/file?a=query#foo");
		assertSame("http://predic8.d%C3%A4e/path/file?a=query#foo");
		assertError("htt\u00E4p://predic8.de/path/file?a=query#foo", "/path/file", "a=query");
		assertError("htt%C3%A4p://predic8.de/path/file?a=query#foo", "/path/file", "a=query");
	}

	@Test
	public void testIllegalCharacter() {
		assertError("http:///test?a=q{uery#foo", "/test", "a=q{uery");
		assertError("http:///te{st?a=query#foo", "/te{st", "a=query");
		assertError("http://pre{dic8.de/test?a=query#foo", "/test", "a=query");
	}

	@Test
	void getScheme() throws URISyntaxException {
		checkGetSchemeCustomParsing(false);
	}

	@Test
	void getSchemeCustom() throws URISyntaxException {
		checkGetSchemeCustomParsing(true);
	}

	private void checkGetSchemeCustomParsing(boolean custom) throws URISyntaxException {
		assertEquals("http", new URI("http://predic8.de",custom).getScheme());
		assertEquals("https", new URI("https://predic8.de",custom).getScheme());
	}

    @Test
    void getHost() throws URISyntaxException {
		checkGetHost(false);
    }

	@Test
	void getHostCustom() throws URISyntaxException {
		checkGetHost(true);
	}

	private void checkGetHost(boolean custom) throws URISyntaxException {
		assertEquals("predic8.de", new URI("http://predic8.de/foo",custom).getHost());
		assertEquals("predic8.de", new URI("http://user:pwd@predic8.de:8080/foo",custom).getHost());
		assertEquals("predic8.de", new URI("http://predic8.de:8080/foo",custom).getHost());
		assertEquals("predic8.de", new URI("https://predic8.de/foo",custom).getHost());
		assertEquals("predic8.de", new URI("https://predic8.de:8443/foo",custom).getHost());
	}

	@Test
	void getPort() throws URISyntaxException {
		getPortCustomParsing(false);
	}

	@Test
	void getPortCustom() throws URISyntaxException {
		getPortCustomParsing(true);
	}

	/**
	 * Default port should be returned as unknown.
	 */
	@Test
	void urlStandardBehaviour() throws URISyntaxException {
		assertEquals(-1, new java.net.URI("http://predic8.de/foo").getPort());
	}

	private void getPortCustomParsing(boolean custom) throws URISyntaxException {
		assertEquals(-1, new URI("http://predic8.de/foo",custom).getPort());
		assertEquals(-1, new URI("https://predic8.de/foo",custom).getPort());
		assertEquals(8090, new URI("http://predic8.de:8090/foo",custom).getPort());
		assertEquals(8443, new URI("https://predic8.de:8443/foo",custom).getPort());
		assertEquals(8090, new URI("http://user:pwd@predic8.de:8090/foo",custom).getPort());
		assertEquals(8443, new URI("https://user:pwd@predic8.de:8443/foo",custom).getPort());
	}

	private void assertSame(String uri) {
		assertSame(uri, false);
	}

	private void assertSame(String uri, boolean mayDiffer) {
		try {
			URI u1 = new URI(uri, false);
			URI u2 = new URI(uri, true);

			if (!mayDiffer) {
				assertEquals(u1.getPath(), u2.getPath());
				assertEquals(u1.getQuery(), u2.getQuery());
				assertEquals(u1.getRawQuery(), u2.getRawQuery());
			}
			assertEquals(u1.toString(), u2.toString());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private void assertError(String uri, String path, String query) {
		try {
			new URI(uri, false);
			fail("Expected URISyntaxException.");
		} catch (URISyntaxException e) {
			// do nothing
		}
		try {
			URI u = new URI(uri, true);
			assertEquals(path, u.getPath());
			assertEquals(query, u.getQuery());
			u.getRawQuery();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}


}
