package com.predic8.membrane.core.http.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Header extends AbstractXmlElement {
	
	public static final String ELEMENT_NAME = "header";

	private String value;
	private String name;
	
	public Header() {}
	
	public Header(String name, String value) {
		this.name = name;
		this.value = value;
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		name = token.getAttributeValue("", name);
	}

	@Override
	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		value = token.getText();
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		out.writeAttribute("name", name);
		out.writeCharacters(value);
		out.writeEndElement();		
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
}
