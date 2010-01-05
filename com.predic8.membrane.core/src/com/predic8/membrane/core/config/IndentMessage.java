package com.predic8.membrane.core.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class IndentMessage extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "indentMessage";
	
	private boolean value;
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
	    value = Boolean.parseBoolean(token.getText());	
	}

	public boolean getValue() {
		return value;
	}
	
	public void setValue(boolean value) {
		this.value = value;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		out.writeCharacters(Boolean.toString(value));
		out.writeEndElement();
	}
	
}
