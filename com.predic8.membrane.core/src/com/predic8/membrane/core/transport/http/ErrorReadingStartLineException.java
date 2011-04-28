package com.predic8.membrane.core.transport.http;

public class ErrorReadingStartLineException extends RuntimeException {

	private static final long serialVersionUID = -6998133859472737055L;
	
	private String startLine;
	
	public String getStartLine() {
		return startLine;
	}
	
	public void setStartLine(String startLine) {
		this.startLine = startLine;
	}
}
