package com.predic8.membrane.core.interceptor.acl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.config.AbstractXMLElement;

public class Ip extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "ip";
	
	private String value;
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
	
	@Override
	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		value = token.getText();
	}
	
}
