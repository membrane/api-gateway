package com.predic8.membrane.annot;

import javax.lang.model.element.Element;

public class ProcessingException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	private Element[] e;
	
	public ProcessingException(String message, Element... e) {
		super(message);
		this.e = e;
	}
	
	public Element[] getElements() {
		return e;
	}
}