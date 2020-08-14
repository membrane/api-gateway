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
package com.predic8.membrane.core.interceptor.ws_addressing;

import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.predic8.membrane.core.exchange.Exchange;


public class WsaEndpointRewriter {
	private static final String ADDRESSING_URI = "http://www.w3.org/2005/08/addressing";

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
								addRewrittenAddressElement(eventWriter, url, port, e.asStartElement());

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
		return startElement.getName().equals(new QName(ADDRESSING_URI, "MessageID"));
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
		return startElement.getName().equals(new QName(ADDRESSING_URI, "ReplyTo"));
	}

	private boolean isReplyTo(EndElement endElement) {
		return endElement.getName().equals(new QName(ADDRESSING_URI, "ReplyTo"));
	}

	private boolean isAddress(StartElement startElement) {
		return startElement.getName().equals(new QName(ADDRESSING_URI, "Address"));
	}
}