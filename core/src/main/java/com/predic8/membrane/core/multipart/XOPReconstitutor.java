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

package com.predic8.membrane.core.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.annotation.concurrent.ThreadSafe;
import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.MultipartStream.MalformedStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.MessageUtil;

/**
 * Reassemble a multipart XOP message (see
 * http://en.wikipedia.org/wiki/XML-binary_Optimized_Packaging and
 * http://www.w3.org/TR/xop10/ ) into one stream (that can be used for schema
 * validation, for example).
 */
@ThreadSafe
public class XOPReconstitutor {
	private static Logger log = LoggerFactory.getLogger(XOPReconstitutor.class.getName());
	private static final String XOP_NAMESPACE_URI = "http://www.w3.org/2004/08/xop/include";

	private final XMLInputFactory xmlInputFactory;

	public XOPReconstitutor() {
		xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	public InputStream reconstituteIfNecessary(Message message) throws XMLStreamException, IOException {
		try {
			Message reconstitutedMessage = getReconstitutedMessage(message);
			if (reconstitutedMessage != null)
				return reconstitutedMessage.getBodyAsStream();
		} catch (Exception e) {
			log.warn("", e);
		}
		return MessageUtil.getContentAsStream(message);
	}

	private XMLEventReader createEventReaderFromStream(InputStream is) throws XMLStreamException {
		synchronized (xmlInputFactory) {
			return xmlInputFactory.createXMLEventReader(is);
		}
	}

	/**
	 * @return reassembled SOAP message or null if message is not SOAP or not multipart
	 */
	public Message getReconstitutedMessage(Message message) throws ParseException, MalformedStreamException, IOException, EndOfStreamException, XMLStreamException, FactoryConfigurationError {
		ContentType contentType = message.getHeader().getContentTypeObject();
		if (contentType == null || contentType.getPrimaryType() == null)
			return null;
		if (!contentType.getPrimaryType().equals("multipart")
				|| !contentType.getSubType().equals("related"))
			return null;

		String type = contentType.getParameter("type");
		if (!"application/xop+xml".equals(type))
			return null;
		String start = contentType.getParameter("start");
		if (start == null)
			return null;
		String boundary = contentType.getParameter("boundary");
		if (boundary == null)
			return null;

		HashMap<String, Part> parts = split(message, boundary);
		Part startPart = parts.get(start);
		if (startPart == null)
			return null;

		ContentType innerContentType = new ContentType(startPart.getHeader().getContentType());
		if (!innerContentType.getPrimaryType().equals("application")
				|| !innerContentType.getSubType().equals("xop+xml"))
			return null;

		byte[] body = fillInXOPParts(startPart.getInputStream(), parts);

		Message m = new Message(){
			@Override
			protected void parseStartLine(InputStream in) throws IOException,
			EndOfStreamException {
				throw new RuntimeException("not implemented.");
			}

			@Override
			public String getStartLine() {
				throw new RuntimeException("not implemented.");
			}
		};
		m.setBodyContent(body);

		String reconstitutedContentType = innerContentType.getParameter("type");
		if (reconstitutedContentType != null)
			m.getHeader().add(Header.CONTENT_TYPE, reconstitutedContentType);

		return m;
	}

	@SuppressWarnings("deprecation")
	private HashMap<String, Part> split(Message message, String boundary)
			throws IOException, EndOfStreamException, MalformedStreamException {
		HashMap<String, Part> parts = new HashMap<String, Part>();

		MultipartStream multipartStream = new MultipartStream(MessageUtil.getContentAsStream(message), boundary.getBytes(Constants.UTF_8_CHARSET));
		boolean nextPart = multipartStream.skipPreamble();
		while(nextPart) {
			Header header = new Header(multipartStream.readHeaders());
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			multipartStream.readBodyData(baos);

			// see http://www.iana.org/assignments/transfer-encodings/transfer-encodings.xml
			String cte = header.getFirstValue("Content-Transfer-Encoding");
			if (cte != null &&
					!cte.equals("binary") &&
					!cte.equals("8bit") &&
					!cte.equals("7bit"))
				throw new RuntimeException("Content-Transfer-Encoding '" + cte + "' not implemented.");


			Part part = new Part(header, baos.toByteArray());
			String id = part.getContentID();
			if (id != null) {
				parts.put(id, part);
			}

			nextPart = multipartStream.readBoundary();
		}
		return parts;
	}

	private byte[] fillInXOPParts(InputStream inputStream,
			HashMap<String, Part> parts) throws XMLStreamException, FactoryConfigurationError {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(baos);

		try {
			XMLEventReader parser = createEventReaderFromStream(inputStream);

			boolean xopIncludeOpen = false;

			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();

				if (event instanceof StartElement) {
					StartElement start = (StartElement)event;
					if (XOP_NAMESPACE_URI.equals(start.getName().getNamespaceURI()) &&
							start.getName().getLocalPart().equals("Include")) {
						String href = start.getAttributeByName(new QName("href")).getValue();

						if (href.startsWith("cid:"))
							href = href.substring(4);

						Part p = parts.get("<" + href + ">");
						if (p == null)
							throw new RuntimeException("Did not find multipart with id " + href);

						writer.add(p.asXMLEvent());
						xopIncludeOpen = true;
						continue;
					}
				} else if (event instanceof EndElement) {
					EndElement start = (EndElement)event;
					if (XOP_NAMESPACE_URI.equals(start.getName().getNamespaceURI()) &&
							start.getName().getLocalPart().equals("Include") &&
							xopIncludeOpen) {
						xopIncludeOpen = false;
						continue;
					}
				}

				writer.add(event);
			}
			writer.flush();
		} catch (XMLStreamException e) {
			log.warn("Received not-wellformed XML.");
			return null;
		}
		return baos.toByteArray();
	}

}
