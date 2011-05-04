package com.predic8.membrane.core.http.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Host extends AbstractXmlElement {
	public static final String ELEMENT_NAME = "host";

	private String value;
	
	public Host(String host) {
		value = host;
	}

	public Host() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		value = token.getText();
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		out.writeCharacters(value);
		out.writeEndElement();		
	}
	
	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
