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
package com.predic8.membrane.core.interceptor.soap.ws_addressing;

import com.predic8.membrane.core.exchange.*;

import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;

/**
 * Unfinished! Almost done!
 */
public class WsaEndpointRewriter {
	private static final String ADDRESSING_URI = "http://www.w3.org/2005/08/addressing";
	private static final String ADDRESSING_URI_OLD = "http://schemas.xmlsoap.org/ws/2004/08/addressing";

	public static final QName WSA_ADDRESS_QNAME = new QName(ADDRESSING_URI, "Address");
	public static final QName WSA_ADDRESS_OLD_QNAME = new QName(ADDRESSING_URI_OLD, "Address");


	private final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
	private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
	private final DecoupledEndpointRegistry registry;

	public WsaEndpointRewriter(DecoupledEndpointRegistry registry) {
		this.registry = registry;
	}

	public void rewriteEndpoint(InputStream reader, OutputStream writer, int port, Exchange exc) throws XMLStreamException {
		XMLEventReader parser = inputFactory.createXMLEventReader(reader);
		XMLEventWriter eventWriter = XMLOutputFactory.newInstance().createXMLEventWriter(writer);

		String id = null;
		String url = null;

		skip:
			while (parser.hasNext()) {
				XMLEvent e = parser.nextEvent();

				if (e.isStartElement()) {
					if (isReplyTo(e.asStartElement())) {
						while (e.isStartElement() || !isReplyTo(e.asEndElement())) {
							if (e.isStartElement() && isAddress(e.asStartElement())) {
								url = parser.getElementText();
								// Unfinised here the URL Rewrite
								addRewrittenAddressElement(eventWriter, "Should be rewritten! Not implemented yet!", port, e.asStartElement());

								continue skip;
							}

							eventWriter.add(e);
							e = parser.nextTag();
						}
					}

					if (isMessageId(e.asStartElement())) {
						id = parser.getElementText();
						exc.setProperty("messageId", id);
						addMessageIdElement(eventWriter, id, e.asStartElement());

						continue skip;
					}
				}

				eventWriter.add(e);
			}

		registry.register(id, url);
	}

	private void addMessageIdElement(XMLEventWriter writer, String id, StartElement startElement) throws XMLStreamException {
		writer.add(eventFactory.createStartElement("", startElement.getName().getNamespaceURI(),
				startElement.getName().getLocalPart(), startElement.getAttributes(), startElement.getNamespaces(),
				startElement.getNamespaceContext()));
		writer.add(eventFactory.createCharacters(id));
		writer.add(eventFactory.createEndElement("", startElement.getName().getNamespaceURI(),
				startElement.getName().getLocalPart(), startElement.getNamespaces()));
	}

	private boolean isMessageId(StartElement startElement) {
		return startElement.getName().equals(new QName(ADDRESSING_URI, "MessageID")) ||
			   startElement.getName().equals(new QName(ADDRESSING_URI_OLD, "MessageID"));
	}

	private void addRewrittenAddressElement(XMLEventWriter writer, String address, int port, StartElement startElement) throws XMLStreamException {
		writer.add(eventFactory.createStartElement("", startElement.getName().getNamespaceURI(),
				startElement.getName().getLocalPart(), startElement.getAttributes(), startElement.getNamespaces(),
				startElement.getNamespaceContext()));
		writer.add(eventFactory.createCharacters(address.replaceFirst(":\\d+/", ":" + port + "/")));
		writer.add(eventFactory.createEndElement("", startElement.getName().getNamespaceURI(),
				startElement.getName().getLocalPart(), startElement.getNamespaces()));
	}

	private boolean isReplyTo(StartElement startElement) {
		return startElement.getName().equals(new QName(ADDRESSING_URI, "ReplyTo")) ||
			   startElement.getName().equals(new QName(ADDRESSING_URI_OLD, "ReplyTo"));
	}

	private boolean isReplyTo(EndElement endElement) {
		return endElement.getName().equals(new QName(ADDRESSING_URI, "ReplyTo")) ||
			   endElement.getName().equals(new QName(ADDRESSING_URI_OLD, "ReplyTo"));
	}

	private boolean isAddress(StartElement startElement) {
		return startElement.getName().equals(WSA_ADDRESS_QNAME) || startElement.getName().equals(WSA_ADDRESS_OLD_QNAME);
	}
}