package com.predic8.membrane.core.config;

import java.io.StringWriter;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public class AbstractXmlElement implements XMLElement{

	public AbstractXmlElement() {
		super();
	}

	public XMLElement parse(XMLStreamReader token) throws XMLStreamException {
	    parseAttributes(token);
	    while(token.hasNext()) {
	      if(token.isStartElement()) {
	        parseChildren(token, token.getName().getLocalPart());
	      }
	      if (token.isCharacters()) {
	    	  parseCharacters(token);
	      }
	      if(token.isEndElement() && token.getName().getLocalPart() == getElementName())
	    	  break;
	      if(token.hasNext()) 
	    	  token.next();
	    }
	    return this;
	  }

	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		  
	  }

	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		  
	  }

	protected void parseChildren(XMLStreamReader token, String child)
			throws XMLStreamException {
				  
			  }

	protected String getElementName() {
		  return null;
	  }

	public void write(XMLStreamWriter out) throws XMLStreamException {
		  
	 }

	protected boolean getBoolean(XMLStreamReader token, String attr) {
		return "true".equals(token.getAttributeValue("", attr)) ? true : false;
	}

	public String toXml() throws Exception {
		StringWriter sw = new StringWriter(); 
		XMLStreamWriter w = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);
		w.writeStartDocument();
		write(w);
		w.writeEndDocument();
		return sw.toString();
	}
}