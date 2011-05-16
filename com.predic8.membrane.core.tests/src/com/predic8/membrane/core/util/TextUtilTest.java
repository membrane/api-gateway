package com.predic8.membrane.core.util;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class TextUtilTest {

	@Test
	public void testGlob() {
		assertTrue(TextUtil.glob("*.predic8.de", "pc55.predic8.de"));
		assertFalse(TextUtil.glob("*.predic8.com", "pc55.predic8.de"));
	}

	@Test
	public void testGlobToExp() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("*.predic8.de"));
		assertTrue(pattern.matcher("hgsjagdjhsa.predic8.de").matches());
		assertTrue(pattern.matcher("jhkj.predic8.de").matches());
		assertFalse(pattern.matcher("jhkj.predic8.com").matches());
		assertFalse(pattern.matcher("jhkj.predic8.comhh").matches());
	}
}
