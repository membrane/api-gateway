/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Checks whether an InputStream is XML and contains any of a set of element names.
 * The element names can have a namespace (in which case it also has to match) or not
 * (if the namespace of an element passed to the constructor is the default namespace,
 * it is treated as "wildcard namespace").
 */
@ThreadSafe
public class XMLElementFinder {
	private static XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	private final HashSet<QName> elements = new HashSet<QName>();
	private final boolean usesWildcardNamespace;

	public XMLElementFinder(List<QName> elements) {
		boolean usesWildcardNamespace = false;
		for (QName element : elements) {
			this.elements.add(element);
			if (element.getNamespaceURI() == XMLConstants.NULL_NS_URI)
				usesWildcardNamespace = true;
		}
		this.usesWildcardNamespace = usesWildcardNamespace;
	}

	/**
	 * @return true, if the input is well-formed XML and any of the elements were found
	 */
	public boolean matches(InputStream is) {
		try {
			XMLEventReader parser;
			synchronized (xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(is);
			}

			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();
				if (event.isStartElement()) {
					StartElement startElement = (StartElement)event;
					if (elements.contains(startElement.getName()))
						return true;

					if (usesWildcardNamespace) {
						QName onlyLocal = new QName(startElement.getName().getLocalPart());
						if (elements.contains(onlyLocal))
							return true;
					}
				}
			}
			return false;
		} catch (XMLStreamException e) {
			return false;
		}
	}

}
