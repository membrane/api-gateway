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
package com.predic8.membrane.core.http.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.Message;

class XMLBody extends AbstractXmlElement {

	private static XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	private final Message msg;

	public XMLBody(Message msg) {
		this.msg = msg;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeAttribute("type", "xml");

		XMLStreamReader parser;
		synchronized(xmlInputFactory) {
			parser = xmlInputFactory.createXMLStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset());
		}

		boolean endDoc = false;
		while (parser.hasNext()) {
			parser.next();

			switch (parser.getEventType()) {
			case XMLEvent.START_ELEMENT:
				final String localName = parser.getLocalName();
				final String namespaceURI = parser.getNamespaceURI();
				if (namespaceURI != null && namespaceURI.length() > 0) {
					final String prefix = parser.getPrefix();
					if (prefix != null)
						out.writeStartElement(prefix, localName, namespaceURI);
					else
						out.writeStartElement(namespaceURI, localName);
				} else {
					out.writeStartElement(localName);
				}

				for (int i = 0, len = parser.getNamespaceCount(); i < len; i++) {
					out.writeNamespace(parser.getNamespacePrefix(i), parser.getNamespaceURI(i));
				}

				for (int i = 0, len = parser.getAttributeCount(); i < len; i++) {
					String attUri = parser.getAttributeNamespace(i);
					if (attUri != null)
						out.writeAttribute(attUri, parser.getAttributeLocalName(i), parser.getAttributeValue(i));
					else
						out.writeAttribute(parser.getAttributeLocalName(i), parser.getAttributeValue(i));
				}
				break;
			case XMLEvent.END_ELEMENT:
				out.writeEndElement();
				break;
			case XMLEvent.SPACE:
			case XMLEvent.CHARACTERS:
				out.writeCharacters(parser.getTextCharacters(), parser.getTextStart(), parser.getTextLength());
				break;
			case XMLEvent.PROCESSING_INSTRUCTION:
				out.writeProcessingInstruction(parser.getPITarget(), parser.getPIData());
				break;
			case XMLEvent.CDATA:
				out.writeCData(parser.getText());
				break;
			case XMLEvent.COMMENT:
				out.writeComment(parser.getText());
				break;
			case XMLEvent.ENTITY_REFERENCE:
				out.writeEntityRef(parser.getLocalName());
				break;
			case XMLEvent.START_DOCUMENT:
				// ignore
				break;
			case XMLEvent.END_DOCUMENT:
				endDoc = true;
				break;
			case XMLEvent.DTD:
				// ignore
				break;
			}
		}
		if (!endDoc)
			throw new RuntimeException("XML document has not ended.");
	}

}
