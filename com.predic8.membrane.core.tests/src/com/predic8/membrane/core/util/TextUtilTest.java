package com.predic8.membrane.core.util;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class TextUtilTest {

	@Test
	public void testGlobToExpStarPrefix() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("*.predic8.de"));
		assertTrue(pattern.matcher("hgsjagdjhsa.predic8.de").matches());
		assertTrue(pattern.matcher("jhkj.predic8.de").matches());
		assertFalse(pattern.matcher("jhkj.predic8.com").matches());
	}
	
	@Test
	public void testGlobToExpStarSuffix() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("predic8.*"));
		assertTrue(pattern.matcher("predic8.de").matches());
		assertTrue(pattern.matcher("predic8.com").matches());
		assertFalse(pattern.matcher("jhkj.predic8.de").matches());
	}
	
	@Test
	public void testGlobToExpStarInfix() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("www.*.de"));
		assertTrue(pattern.matcher("www.predic8.de").matches());
		assertTrue(pattern.matcher("www.oio.de").matches());
		assertFalse(pattern.matcher("www.predic8.com").matches());
	}
}
