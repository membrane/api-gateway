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

package com.predic8.membrane.core.ws.relocator;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Relocator {
	XMLEventWriter writer;
	private final String SOAP_NS = "http://schemas.xmlsoap.org/wsdl/soap/";
	private final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
	private final QName ADDRESS = new QName(SOAP_NS, "address");
	private final QName IMPORT = new QName(XSD_NS, "import");
	private final QName INCLUDE = new QName(XSD_NS, "include");
	
	private String host;
	private int port;
	
	private class ReplaceIterator implements Iterator<Attribute> {

		Iterator<Attribute> attrs;
		String replace;
		
		public ReplaceIterator(String replace, Iterator<Attribute> attrs) {
			this.replace = replace;
			this.attrs = attrs;
		}
		
		public boolean hasNext() {
			return attrs.hasNext();
		}

		public Attribute next() {
			Attribute atr = attrs.next();
			if ( atr.getName().equals(new QName(replace)) && atr.getValue().startsWith("http") ) {
				String newLocation = "";
				try {
					URL oldURL = new URL(atr.getValue());
					newLocation = new URL(oldURL.getProtocol(), host, port, oldURL.getFile()).toString();
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
				
				XMLEventFactory fac = XMLEventFactory.newInstance();
				return fac.createAttribute(replace,newLocation);
			}
			return atr;
		}

		public void remove() {
			attrs.remove();
		}
		
	}
	
	public Relocator( OutputStream ostream, String host, int port ) throws Exception {
		XMLOutputFactory output = XMLOutputFactory.newInstance();
		this.writer = output.createXMLEventWriter(ostream);
		this.host = host;
		this.port = port;
	
	}
	
	public void relocate( InputStream istream ) throws Exception {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLEventReader parser = factory.createXMLEventReader(istream);
		
		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();
			if ( event.isStartElement() ) {
				if ( event.asStartElement().getName().equals(ADDRESS)) {
					event = replace(event, "location");
				} else if ( event.asStartElement().getName().equals(INCLUDE)) {
					event = replace(event, "schemaLocation");
				} else if ( event.asStartElement().getName().equals(IMPORT)) {
					event = replace(event, "schemaLocation");
				}
			}
			writer.add(event);
		}
	}

	@SuppressWarnings("unchecked")
	private XMLEvent replace(XMLEvent event, String attribute) {
		XMLEventFactory fac = XMLEventFactory.newInstance();
		StartElement startElement = event.asStartElement();
		return fac.createStartElement(startElement.getName(), 
				new ReplaceIterator(attribute, startElement.getAttributes()), 
				startElement.getNamespaces());
	}
}
