package com.predic8.membrane.core.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;


public class Path extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "path";
	
	private String value;
	
	private Boolean regExp;
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		value = token.getText();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		regExp = Boolean.parseBoolean(token.getAttributeValue("", "isRegExp"));
	}

	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		out.writeAttribute("isRegExp", regExp.toString());
		out.writeCharacters(value);
		out.writeEndElement();
	}

	public boolean isRegExp() {
		return regExp;
	}

	public void setRegExp(boolean regExp) {
		this.regExp = regExp;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
	
}
