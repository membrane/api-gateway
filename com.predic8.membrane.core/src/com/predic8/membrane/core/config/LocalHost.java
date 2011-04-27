package com.predic8.membrane.core.config;

public class LocalHost extends CharactersElement {

public static final String ELEMENT_NAME = "localHost";
	
	public LocalHost() {
		
	}

	public LocalHost(String value) {
		super(value);
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
}
