/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
		
	
