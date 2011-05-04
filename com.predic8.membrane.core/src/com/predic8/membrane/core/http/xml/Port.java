package com.predic8.membrane.core.http.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Port extends AbstractXmlElement {
	public static final String ELEMENT_NAME = "port";

	int value;
	
	public Port(int port) {
		value = port;
	}

	public Port() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		value = Integer.valueOf(token.getText());
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		out.writeCharacters(""+value);
		out.writeEndElement();		
	}
	
	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

}
