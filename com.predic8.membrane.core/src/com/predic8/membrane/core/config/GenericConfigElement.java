package com.predic8.membrane.core.config;

import java.util.*;

import javax.xml.stream.*;

public class GenericConfigElement extends AbstractXmlElement {

	private Map<String, String> attributes = new HashMap<String, String>();
	private String name;
	private AbstractXmlElement childParser; 
	
	public GenericConfigElement() {}
	
	public GenericConfigElement(AbstractXmlElement p) {
		childParser = p;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (childParser != null) { 
			childParser.parseChildren(token, child);
			return;
		}
			
		super.parseChildren(token, child);
	}
		
	@Override
	protected void parseAttributes(XMLStreamReader token)
			throws XMLStreamException {
		name = token.getLocalName();
		
		for ( int i = 0; i < token.getAttributeCount(); i++) {
			attributes.put(token.getAttributeLocalName(i), token.getAttributeValue(i));
		}
	}

	public String getAttributeOrDefault(String name, String def) {
		if (attributes.containsKey(name)) 
			return attributes.get(name);
		return ""+def;
	}

	public String getAttribute(String name) {
		return attributes.get(name);
	}
	
	@Override
	protected String getElementName() {
		return name;
	}

}
