package com.predic8.membrane.core.util;

import static org.junit.Assert.*;

import java.util.regex.Pattern;

import org.junit.Test;

public class TextUtilTest {

	@Test
	public void testGlobToExpStarPrefixHost() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("*.predic8.de"));
		assertTrue(pattern.matcher("hgsjagdjhsa.predic8.de").matches());
		assertTrue(pattern.matcher("jhkj.predic8.de").matches());
		assertFalse(pattern.matcher("jhkj.predic8.com").matches());
	}
	
	@Test
	public void testGlobToExpStarSuffixHost() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("predic8.*"));
		assertTrue(pattern.matcher("predic8.de").matches());
		assertTrue(pattern.matcher("predic8.com").matches());
		assertFalse(pattern.matcher("jhkj.predic8.de").matches());
	}
	
	@Test
	public void testGlobToExpStarInfixHost() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("www.*.de"));
		assertTrue(pattern.matcher("www.predic8.de").matches());
		assertTrue(pattern.matcher("www.oio.de").matches());
		assertFalse(pattern.matcher("www.predic8.com").matches());
		assertFalse(pattern.matcher("www.predic8.co.uk").matches());
		assertFalse(pattern.matcher("services.predic8.de").matches());
	}
	
	@Test
	public void testGlobToExpStarPrefixIp() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("*.68.5.122"));
		assertTrue(pattern.matcher("192.68.5.122").matches());
		assertFalse(pattern.matcher("192.68.5.123").matches());
	}
	
	@Test
	public void testGlobToExpStarSuffixIp() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("192.68.7.*"));
		assertTrue(pattern.matcher("192.68.7.12").matches());
		assertTrue(pattern.matcher("192.68.7.4").matches());
		assertFalse(pattern.matcher("192.68.6.12").matches());
	}
	
	@Test
	public void testGlobToExpStarInfixIp() throws Exception {
		Pattern pattern = Pattern.compile(TextUtil.globToRegExp("192.68.*.15"));
		assertTrue(pattern.matcher("192.68.5.15").matches());
		assertTrue(pattern.matcher("192.68.24.15").matches());
		assertFalse(pattern.matcher("192.68.24.12").matches());
	}
	
}
