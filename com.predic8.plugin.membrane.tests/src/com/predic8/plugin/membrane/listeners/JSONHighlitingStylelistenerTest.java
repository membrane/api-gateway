package com.predic8.plugin.membrane.listeners;

import static org.junit.Assert.*;

import org.junit.*;

public class JSONHighlitingStylelistenerTest {

	@Before
	public void setUp() throws Exception {
		
	}
	
	@Test
	public void testPatternKeyUnquoted() throws Exception {
		
		assertTrue(JSONHighlitingStylelistener.patternKey.matcher("title :").matches());
		
	}
	
}
