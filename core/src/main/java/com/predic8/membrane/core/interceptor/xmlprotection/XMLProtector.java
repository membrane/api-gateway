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

package com.predic8.membrane.core.interceptor.xmlprotection;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Iterator;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters XML streams, removing potentially malicious elements:
 * <ul>
 * <li>DTDs can be removed.</li>
 * <li>The length of element names can be limited.</li>
 * <li>The number of attibutes per element can be limited.</li>
 * </ul>
 *
 * If {@link #protect(InputStreamReader)} returns false, an unrecoverable error has
 * occurred (such as not-wellformed XML or an element name length exceeded the limit),
 * the {@link OutputStreamWriter} is left at this position: It should be discarded and
 * an error response should be returned to the requestor.
 */
public class XMLProtector {
	private static Logger log = LoggerFactory.getLogger(XMLProtector.class.getName());
	private static XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	private XMLEventWriter writer;
	private final int maxAttibuteCount;
	private final int maxElementNameLength;
	private final boolean removeDTD;

	public XMLProtector(OutputStreamWriter osw, boolean removeDTD, int maxElementNameLength, int maxAttibuteCount) throws Exception {
		this.writer = XMLOutputFactory.newInstance().createXMLEventWriter(osw);
		this.removeDTD = removeDTD;
		this.maxElementNameLength = maxElementNameLength;
		this.maxAttibuteCount = maxAttibuteCount;
	}

	public boolean protect(InputStreamReader isr) {
		try {
			XMLEventReader parser;
			synchronized(xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(isr);
			}

			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();
				if (event.isStartElement()) {
					StartElement startElement = (StartElement)event;
					if (maxElementNameLength != -1)
						if (startElement.getName().getLocalPart().length() > maxElementNameLength) {
							log.warn("Element name length: Limit exceeded.");
							return false;
						}
					if (maxAttibuteCount != -1) {
						@SuppressWarnings("rawtypes")
						Iterator i = startElement.getAttributes();
						for (int attributeCount = 0; i.hasNext(); i.next())
							if (++attributeCount == maxAttibuteCount) {
								log.warn("Number of attributes per element: Limit exceeded.");
								return false;
							}
					}
				} if (event instanceof javax.xml.stream.events.DTD) {
					if (removeDTD) {
						log.debug("removed DTD.");
						continue;
					}
				}
				writer.add(event);
			}
			writer.flush();
		} catch (XMLStreamException e) {
			log.warn("Received not-wellformed XML.");
			return false;
		}
		return true;
	}

}
