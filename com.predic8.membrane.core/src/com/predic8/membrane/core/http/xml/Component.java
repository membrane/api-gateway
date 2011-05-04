package com.predic8.membrane.core.http.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Component extends AbstractXmlElement {
	
	public static final String ELEMENT_NAME = "component";

	String value;
	
	public Component() {}
	
	public Component(String c) {
		value = c;
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

}
