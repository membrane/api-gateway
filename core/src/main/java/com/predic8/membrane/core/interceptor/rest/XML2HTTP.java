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
package com.predic8.membrane.core.interceptor.rest;

import java.io.StringWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.xml.Request;

/**
 * Reverse of {@link Request#write(XMLStreamWriter)}.
 */
public class XML2HTTP {
	private static XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	private static XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	private static Log log = LogFactory.getLog(XML2HTTP.class);

	/**
	 * Checks, if the response contains an XML doc with NS {@link Constants#HTTP_NS}.
	 * If it does, the HTTP data (status, headers, body) is extracted from the doc
	 * and set as the response.
	 * 
	 * Reverse of {@link Request#write(XMLStreamWriter)}.
	 */
	public static void unwrapResponseIfNecessary(Response response) {
		if (MimeType.TEXT_XML_UTF8.equals(response.getHeader().getContentType())) {
			try {
				XMLEventReader parser;
				synchronized(xmlInputFactory) {
					parser = xmlInputFactory.createXMLEventReader(response.getBodyAsStream(), response.getCharset());
				}

				/* States:
				 * 0 = before root element,
				 * 1 = root element has HTTP_NS namespace
				 */
				int state = 0;
				
				while (parser.hasNext()) {
					XMLEvent event = parser.nextEvent();
					switch (state) {
					case 0:
						if (event.isStartElement()) {
							if (Constants.HTTP_NS.equals(event.asStartElement().getName().getNamespaceURI())) {
								state = 1;
							} else {
								return;
							}
						}
						break;
					case 1:
						if (event.isStartElement()) {
							String localName = event.asStartElement().getName().getLocalPart();
							if ("status".equals(localName)) {
								response.setStatusCode(Integer.parseInt(requireAttribute(event.asStartElement(), "code")));
								response.setStatusMessage(slurpCharacterData(parser, event.asStartElement()));
							}
							if ("header".equals(localName)) {
								String key = requireAttribute(event.asStartElement(), "name");
								boolean remove = getAttribute(event.asStartElement(), "remove") != null;
								response.getHeader().removeFields(key);
								if (!remove)
									response.getHeader().add(key, slurpCharacterData(parser, event.asStartElement()));
							}
							if ("body".equals(localName)) {
								String type = requireAttribute(event.asStartElement(), "type");
								if ("plain".equals(type)) {
									response.setBodyContent(slurpCharacterData(parser, event.asStartElement()).getBytes(Constants.UTF_8_CHARSET));
								} else if ("xml".equals(type)) {
									response.setBodyContent(slurpXMLData(parser, event.asStartElement()).getBytes(Constants.UTF_8_CHARSET));
								} else {
									throw new XML2HTTPException("XML-HTTP doc body type '" + type + "' is not supported (only 'plain' or 'xml').");
								}
							}
						}
						break;
					}
				}
			} catch (XMLStreamException e) {
				log.error(e);
			} catch (XML2HTTPException e) {
				log.error(e);
			}
		}
	}
	
	private static String slurpCharacterData(XMLEventReader parser, StartElement sevent) throws XMLStreamException, XML2HTTPException {
		String name = sevent.getName().getLocalPart();
		StringBuilder value = new StringBuilder();
		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();
			if (event.isCharacters()) {
				value.append(event.asCharacters().getData());
			} else if (event.isEndElement()) {
				break;
			} else {
				throw new XML2HTTPException("XML-HTTP doc <"+name+"> element contains non-character data.");
			}
		}
		return value.toString();
	}
	
	private static String slurpXMLData(XMLEventReader parser, StartElement sevent) throws XML2HTTPException, XMLStreamException {
		StringWriter bodyStringWriter = new StringWriter();
		XMLEventWriter bodyWriter = null;
		int depth = 0;
		synchronized(xmlOutputFactory) {
			bodyWriter = xmlOutputFactory.createXMLEventWriter(bodyStringWriter);
		}
		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();

			if (event.isEndElement() && depth == 0) {
				bodyWriter.flush();
				return bodyStringWriter.toString();
			}
			bodyWriter.add(event);
			if (event.isStartElement())
				depth++;
			else if (event.isEndElement())
				depth--;
		}
		throw new XML2HTTPException("Early end of file while reading inner XML document.");
	}

	/**
	 * @return the attribute's value
	 * @throws XML2HTTPException if no such attribute exists
	 */
	private static String requireAttribute(StartElement element, String name) throws XML2HTTPException {
		Attribute attribute = element.getAttributeByName(new QName(name));
		if (attribute == null)
			throw new XML2HTTPException("XML-HTTP doc <" + element.getName().getLocalPart() + "> element does not have @"+name+" attribute.");
		return attribute.getValue();
	}
	
	/**
	 * @return the attribute's value or null, if the element has no attribute with the given name.
	 */
	private static String getAttribute(StartElement element, String name) throws XML2HTTPException {
		Attribute attribute = element.getAttributeByName(new QName(name));
		if (attribute == null)
			return null;
		return attribute.getValue();
	}

	
	private static class XML2HTTPException extends Exception {
		private static final long serialVersionUID = 1L;

		public XML2HTTPException(String message) {
			super("REST2SOAP: " + message);
		}
	}

}
