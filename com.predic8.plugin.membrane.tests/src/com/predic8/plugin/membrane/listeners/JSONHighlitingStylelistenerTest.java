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
		StringBuffer buf = new StringBuffer();
		buf.append("'");
		buf.append("title");
		buf.append("'");
		buf.append(" ");
		buf.append(":");
		
		Matcher matcher = JSONHighlitingStylelistener.patternKey.matcher(buf.toString());
		int count = matcher.groupCount();
		for(int i = 0; i < count; i ++) {
			if (matcher.find())
				System.out.println(matcher.group(i));
		}
	}
	
	
	@Test
	public void testPatternKeyUnquoted() throws Exception {
		StringBuffer buf = new StringBuffer();
		buf.append(" ");
		buf.append("title");
		buf.append(" ");
		buf.append(" ");
		buf.append(":");
		
		Matcher matcher = JSONHighlitingStylelistener.patternKeyUnquoted.matcher(buf.toString());
		int count = matcher.groupCount();
		for(int i = 0; i < count; i ++) {
			if (matcher.find())
				System.out.println(matcher.group(i));
		}
		
	}
	
}
