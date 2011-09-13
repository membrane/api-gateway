package com.predic8.plugin.membrane.listeners;

import static org.junit.Assert.*;

import java.util.regex.*;

import org.junit.*;

public class JSONHighlitingStylelistenerTest {

	@Before
	public void setUp() throws Exception {
		
	}
	
	@Test
	public void testPatternKey() throws Exception {
		Matcher matcher = JSONHighlitingStylelistener.patternKey.matcher(" 'title' :");		
		while (matcher.find()) {
			assertEquals("'title'", matcher.group());
			return;
		}
		fail("matcher should match at least one time");
	}
	
	
	@Test
	public void testPatternKeyUnquoted() throws Exception {
		Matcher matcher = JSONHighlitingStylelistener.patternKeyUnquoted.matcher("  title : ");
		while (matcher.find()) {
			assertEquals("title", matcher.group());
			return;
		}
		fail("matcher should match at least one time");
	}
	
}
