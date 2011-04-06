package com.predic8.membrane.core.interceptor.schemavalidation;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class TestErrorHandler implements ErrorHandler {

	private Exception exception; 
	
	public void error(SAXParseException e) throws SAXException {
		exception = e;
	}

	public void fatalError(SAXParseException e) throws SAXException {
		exception = e;
	}

	public void warning(SAXParseException e) throws SAXException {
		
	}
	
	public Exception getException() {
		return exception;
	}
	
	
	public void reset() {
		exception = null;
	}
}
