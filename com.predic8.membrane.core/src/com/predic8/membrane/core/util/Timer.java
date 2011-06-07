package com.predic8.membrane.core.util;

import org.apache.commons.logging.*;

public class Timer {
	private static Log log = LogFactory.getLog(Timer.class.getName());
	
	private long time;
	
	public Timer() {
		time = System.currentTimeMillis();
	}
	
	public void log(String txt) {
		long now = System.currentTimeMillis();
		log.debug(txt+ ":" + (now-time));
		time = System.currentTimeMillis();
	}
}
