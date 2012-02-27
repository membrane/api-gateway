package com.predic8.membrane.examples.util;

import com.predic8.membrane.examples.AbstractConsoleWatcher;

public class BufferLogger extends AbstractConsoleWatcher {

	private StringBuffer b = new StringBuffer();
	private String newline = System.getProperty("line.separator");
	
	@Override
	public void outputLine(boolean error, String line) {
		synchronized(b) {
			b.append(line);
			b.append(newline);
		}
	}
	
	@Override
	public String toString() {
		synchronized(b) {
			return b.toString();
		}
	}
}
