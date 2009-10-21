package com.predic8.membrane.core.ws.magic;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

class Technology {
	public String name;
	public List<Technology> children = new LinkedList<Technology>();
}

public class Magic {

	List<QName> technologies = new LinkedList<QName>();
	
	public List<Technology> scan( InputStream istream ) throws Exception {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLEventReader parser = factory.createXMLEventReader(istream);
		
		technologies.add(new QName("http://schemas.xmlsoap.org/wsdl/","definitions"));
		List<Technology> foundTechnologies = new LinkedList<Technology>();		
		
		parseTechnology(parser, foundTechnologies);
		return foundTechnologies;
	}
	
	private void parseTechnology( XMLEventReader parser, List<Technology> children ) throws XMLStreamException {
		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();
			if ( event.isStartElement() ) {
				int idx = technologies.indexOf(event.asStartElement().getName());
				if ( idx != -1 ) {
					Technology newtech = new Technology();
					newtech.name=event.asStartElement().getName().toString();
					children.add(newtech);
					parseTechnology(parser, newtech.children );
				}
			} else if (event.isEndElement() ) {
				break;
			}
		}		
	}
}
		
	
