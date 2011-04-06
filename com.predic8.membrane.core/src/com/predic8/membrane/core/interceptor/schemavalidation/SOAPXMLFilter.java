package com.predic8.membrane.core.interceptor.schemavalidation;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

public class SOAPXMLFilter extends XMLFilterImpl {

	
	private boolean body;
	
	public SOAPXMLFilter(XMLReader reader) {
		super(reader);
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		
		if (localName.equals("Body")) {
			body = true;
			return;
		}
		
		if (!body)
			return;
		
		super.startElement(uri, localName, qName, atts);
		
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		
		if (localName.equals("Body")) {
			body = false;
			return;
		}
		
		if (!body)
			return;
		
		super.endElement(uri, localName, qName);
	}
	
	
}
