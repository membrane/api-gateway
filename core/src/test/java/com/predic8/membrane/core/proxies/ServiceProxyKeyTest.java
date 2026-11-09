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
package com.predic8.membrane.core.proxies;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;


public class ServiceProxyKeyTest {

	@Test
	@DisplayName("Keys with the same port, host, method and path are equal")
	void equalsAndHashCodeOnValues() {
		var key = new ServiceProxyKey("localhost", "GET", "/foo", 2000);
		var same = new ServiceProxyKey("localhost", "GET", "/foo", 2000);

		assertEquals(key, same);
		assertEquals(key.hashCode(), same.hashCode());
	}

	@Test
	@DisplayName("Port, host, method and path each discriminate")
	void notEqualOnDifferingValues() {
		var key = new ServiceProxyKey("localhost", "GET", "/foo", 2000);

		assertNotEquals(key, new ServiceProxyKey("localhost", "GET", "/foo", 3000));
		assertNotEquals(key, new ServiceProxyKey("other", "GET", "/foo", 2000));
		assertNotEquals(key, new ServiceProxyKey("localhost", "POST", "/foo", 2000));
		assertNotEquals(key, new ServiceProxyKey("localhost", "GET", "/bar", 2000));
	}

	@Test
	void createHostPatternSimple() {
		assertEquals("(\\Qmembrane\\E)", ServiceProxyKey.createHostPattern("membrane"));
	}

	@Test
	void createHostPatternWildcard() {
		assertEquals("(\\Qmembrane\\E.+)", ServiceProxyKey.createHostPattern("membrane*"));
	}

	@Test
	void createHostPatternSpaceAtStart() {
		assertEquals("(\\Qmembrane\\E)", ServiceProxyKey.createHostPattern("     membrane"));
	}

	@Test
	void createHostPatternOnlySpaces() {
		assertEquals("()", ServiceProxyKey.createHostPattern("         "));
	}

	@Test
	void createHostPatternSpaceAtEnd() {
		assertEquals("(\\Qfoo\\E)", ServiceProxyKey.createHostPattern("foo "));
	}

	@Test
	void createHostPatternMultiHost() {
		assertEquals("(\\Qfoo\\E)|(\\Qbar\\E)|(\\Qbaz\\E)", ServiceProxyKey.createHostPattern("foo bar baz"));
	}

	@Test
	void createHostPatternMultiHostSpecialChars() {
		assertEquals("(\\Q\\\\foo\\E)|(\\Q(bar)\\E)|(\\Qbaz!\\E)", ServiceProxyKey.createHostPattern("\\foo (bar) baz!"));
	}

	@Test
	void testSimpleConstructor() {
		ServiceProxyKey key = new ServiceProxyKey(3000);
		assertEquals(3000, key.getPort());
		assertEquals("*", key.getMethod());
		assertEquals("*", key.getHost());
		assertNull(key.getPath());
		assertTrue(key.isPathRegExp());
		assertTrue(key.isHostWildcard());
		assertFalse(key.isUsePathPattern());
	}

	@Test
	void testNoRegExpMatchesPath() {
		ServiceProxyKey key = new ServiceProxyKey("localhost", METHOD_POST, "/axis2/services", 3000);
		key.setPathRegExp(false);

		assertTrue(key.matchesPath("/axis2/services/bla/other"));
		assertFalse(key.matchesPath("/service/we"));
	}

	@Test
	void testRegularExpressionMatchesPath() {
		ServiceProxyKey key = new ServiceProxyKey("localhost", METHOD_POST, ".*FooService", 3000);
		assertTrue(key.matchesPath("/axis2/services/FooService"));
		assertFalse(key.matchesPath("/axis2/services/FooService/Bla"));
	}

	@Test
	void regularExpressionMatchesPathAnyURI() {
		ServiceProxyKey key = new ServiceProxyKey("localhost", METHOD_POST, ".*", 3000);
		assertTrue(key.matchesPath("/axis2/services/FooService"));
		assertTrue(key.matchesPath("/axis2/services/FooService/Bla"));
	}

	@Test
	void hostMatch() {
		testHostMatch("localhost", "localhost");
		testHostMatch("foo.predic8.de", "foo.predic8.de");
		testHostMatch(" foo.predic8.de ", "foo.predic8.de");
		testHostMatch("foo.predic8.de", "foo.predic8.de:80");
		testHostMatch("foo.predic8.de *.predic8.de", "foo.predic8.de");
		testHostMatch("foo.predic8.de *.predic8.de", "bar.predic8.de");
		testHostMatch("foo.predic8.de bar.predic8.de", "baz.predic8.de", false);
		testHostMatch("*.predic8.de", "foo.predic8.de");
		testHostMatch("a b c", "c");
	}

	@Test
	void hostMatchBracketedIpv6() {
		testHostMatch("[::1]", "[::1]");
		testHostMatch("[::1]", "[::1]:2000");
		testHostMatch("[::1]", "[::1]:");
		testHostMatch("foo.predic8.de [::1]", "[::1]:2000");
		testHostMatch("[::1]", "[::2]:2000", false);
	}

	@Test
	void hostMatchBracketedIpv6InvalidSuffix() {
		testHostMatch("[::1]", "[::1]foo", false);
		testHostMatch("[::1]", "[::1]:abc", false);
		testHostMatch("[::1]", "[::1]:2000x", false);
	}

	@Test
	@DisplayName("Host names are compared ignoring ASCII case")
	void hostMatchIgnoresAsciiCase() {
		testHostMatch("foo.predic8.de", "FOO.Predic8.DE");
		testHostMatch("FOO.PREDIC8.DE", "foo.predic8.de");
		testHostMatch("*.Predic8.de", "Foo.PREDIC8.de");
	}

	@Test
	@DisplayName("Non-ASCII characters are not case folded onto ASCII letters")
	void hostMatchDoesNotFoldUnicodeOntoAscii() {
		testHostMatch("kapi.predic8.de", "Kapi.predic8.de", false); // Kelvin sign
		testHostMatch("sapi.predic8.de", "ſapi.predic8.de", false); // long s
		testHostMatch("*.kapi.predic8.de", "a.Kapi.predic8.de", false);
	}

	@Test
	@DisplayName("Characters of the configured host are literals, not regex syntax")
	void hostMatchTreatsHostAsLiteral() {
		testHostMatch("foo.predic8.de", "fooxpredic8.de", false);
		testHostMatch("a+b", "aab", false);
		testHostMatch("a+b", "a+b");
		testHostMatch("(bar)", "(bar)");
	}

	@Test
	@DisplayName("The whole host name has to match, not a part of it")
	void hostMatchIsAnchored() {
		testHostMatch("predic8.de", "foo.predic8.de", false);
		testHostMatch("foo.predic8.de", "foo.predic8.de.example.com", false);
		testHostMatch("*.predic8.de", "foo.predic8.de.example.com", false);
	}

	@Test
	@DisplayName("A wildcard matches one or more characters, dots included")
	void hostWildcardMatchesOneOrMoreCharacters() {
		testHostMatch("*.predic8.de", "a.b.predic8.de");
		testHostMatch("*.predic8.de", ".predic8.de", false);
		testHostMatch("*.predic8.de", "predic8.de", false);
		testHostMatch("api-*.predic8.de", "api-v1.predic8.de");
		testHostMatch("api-*.predic8.de", "api-.predic8.de", false);
		testHostMatch("192.168.*", "192.168.1.5");
		testHostMatch("foo*", "foo", false);
		testHostMatch("*.*.predic8.de", "a.b.predic8.de");
		testHostMatch("*.*.predic8.de", "a.predic8.de", false);
		testHostMatch("foo.example.com *.*.predic8.de", "a.b.c.predic8.de");
	}

	@Test
	@DisplayName("A wildcard does not match line terminators")
	void hostWildcardDoesNotMatchLineTerminators() {
		for (var terminator : List.of("\n", "\r", "\u0085", " ", " "))
			testHostMatch("*.predic8.de", "a%sb.predic8.de".formatted(terminator), false);
	}

	@Test
	@DisplayName("A missing Host header matches only the host wildcard")
	void hostMatchWithoutHostHeader() {
		testHostMatch("foo.predic8.de", null, false);
		testHostMatch("*", null);
	}

	@Test
	@DisplayName("A copied key matches the same hosts as the original")
	void copiedKeyMatchesSameHosts() {
		final var copy = new ServiceProxyKey(new ServiceProxyKey("*.predic8.de", "GET", null, 80));
		assertTrue(copy.matchesHostHeader("foo.predic8.de"));
		assertFalse(copy.matchesHostHeader("foo.example.com"));
	}

	@Test
	@DisplayName("Host names longer than DNS allows do not match")
	void hostMatchRejectsOverlongHostNames() {
		final var maxLength = "a".repeat(255 - ".predic8.de".length()) + ".predic8.de";
		testHostMatch("*.predic8.de", maxLength);
		testHostMatch("*.predic8.de", "a" + maxLength, false);
		testHostMatch("*.predic8.de", maxLength + ":8080");
		testHostMatch("*", "a" + maxLength);
	}

	@Test
	@DisplayName("Matching takes linear time, however many wildcards the host has")
	void hostMatchWithManyWildcardsIsFast() {
		final var key = new ServiceProxyKey("*.*.*.*.*.predic8.de", "GET", null, 80);
		final var host = "a.".repeat(126) + "a";
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertFalse(key.matchesHostHeader(host)));
	}

	private void testHostMatch(String hostArg, String hostHeader) {
		testHostMatch(hostArg, hostHeader, true);
	}

	private void testHostMatch(String hostArg, String hostHeader, boolean result) {
		assertEquals(result, new ServiceProxyKey(hostArg, "GET", null, 80).matchesHostHeader(hostHeader));
	}

	@Test
	void toStringDefault() {
		assertEquals("0.0.0.0:80", new ServiceProxyKey(80).toString());
	}

	@Test
	void toStringTest() {
        assertEquals("localhost:3000 POST /axis2/services", new ServiceProxyKey("localhost", METHOD_POST, "/axis2/services", 3000).toString());
	}
}
