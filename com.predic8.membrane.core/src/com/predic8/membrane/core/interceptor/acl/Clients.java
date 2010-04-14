package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.config.AbstractXMLElement;

public class Clients extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "clients";
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
}
