package com.predic8.membrane.core.util;

import org.apache.commons.logging.*;

public class Timer {
	private static Log log = LogFactory.getLog(Timer.class.getName());
	
	static private long time;
	
	static public void reset() {
		time = System.currentTimeMillis();
	}
	
	static public void log(String txt) {
		long now = System.currentTimeMillis();
		log.debug(txt+ ":" + (now-time));
		time = System.currentTimeMillis();
	}
}
