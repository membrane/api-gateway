package com.predic8.membrane.core.http.xml;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Path extends AbstractXmlElement {
	public static final String ELEMENT_NAME = "path";

	List<Component> components = new ArrayList<Component>();
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Component.ELEMENT_NAME.equals(child)) {
			components.add((Component)new Component().parse(token));
		} 
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		for (Component c : components) {
			c.write(out);
		}
		
		out.writeEndElement();		
	}

	public List<Component> getComponents() {
		return components;
	}

	public void setComponents(List<Component> components) {
		this.components = components;
	}
	
}
