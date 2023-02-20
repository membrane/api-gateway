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

import static com.predic8.membrane.core.util.TextUtil.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class TextUtilTest {

	@Test
	public void testGlobToExpStarPrefixHost() {
		Pattern pattern = Pattern.compile(globToRegExp("*.predic8.de"));
		assertTrue(pattern.matcher("hgsjagdjhsa.predic8.de").matches());
		assertTrue(pattern.matcher("jhkj.predic8.de").matches());
		assertFalse(pattern.matcher("jhkj.predic8.com").matches());
	}

	@Test
	public void testGlobToExpStarSuffixHost() {
		Pattern pattern = Pattern.compile(globToRegExp("predic8.*"));
		assertTrue(pattern.matcher("predic8.de").matches());
		assertTrue(pattern.matcher("predic8.com").matches());
		assertFalse(pattern.matcher("jhkj.predic8.de").matches());
	}

	@Test
	public void testGlobToExpStarInfixHost() {
		Pattern pattern = Pattern.compile(globToRegExp("www.*.de"));
		assertTrue(pattern.matcher("www.predic8.de").matches());
		assertTrue(pattern.matcher("www.oio.de").matches());
		assertFalse(pattern.matcher("www.predic8.com").matches());
		assertFalse(pattern.matcher("www.predic8.co.uk").matches());
		assertFalse(pattern.matcher("services.predic8.de").matches());
	}

	@Test
	public void testGlobToExpStarPrefixIp() {
		Pattern pattern = Pattern.compile(globToRegExp("*.68.5.122"));
		assertTrue(pattern.matcher("192.68.5.122").matches());
		assertFalse(pattern.matcher("192.68.5.123").matches());
	}

	@Test
	public void testGlobToExpStarSuffixIp() {
		Pattern pattern = Pattern.compile(globToRegExp("192.68.7.*"));
		assertTrue(pattern.matcher("192.68.7.12").matches());
		assertTrue(pattern.matcher("192.68.7.4").matches());
		assertFalse(pattern.matcher("192.68.6.12").matches());
	}

	@Test
	public void testGlobToExpStarInfixIp() {
		Pattern pattern = Pattern.compile(globToRegExp("192.68.*.15"));
		assertTrue(pattern.matcher("192.68.5.15").matches());
		assertTrue(pattern.matcher("192.68.24.15").matches());
		assertFalse(pattern.matcher("192.68.24.12").matches());
	}

    @Test
    void getLineFromMultilineStringTest() {
		assertEquals("ccc ccc",TextUtil.getLineFromMultilineString("""
                aaa aaa
                bb bb
                ccc ccc
                ddd dd ddd
                """, 3));
    }

	@Test
	void getLineFromMultilineStringOneLine() {
		assertEquals("aaa aaa",TextUtil.getLineFromMultilineString("""
                aaa aaa
                """, 1));
	}
}