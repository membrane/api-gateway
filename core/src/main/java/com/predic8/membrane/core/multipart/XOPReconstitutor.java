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

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.fileupload.*;
import org.slf4j.*;

import javax.annotation.concurrent.*;
import javax.mail.internet.*;
import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * Reassemble a multipart XOP message (see
 * <a href="http://en.wikipedia.org/wiki/XML-binary_Optimized_Packaging">XML-binary_Optimized_Packaging</a> and
 * <a href="http://www.w3.org/TR/xop10/">xop10</a> ) into one stream (that can be used for schema
 * validation, for example).
 */
@ThreadSafe
public class XOPReconstitutor {
	private static final Logger log = LoggerFactory.getLogger(XOPReconstitutor.class.getName());
	private static final String XOP_NAMESPACE_URI = "http://www.w3.org/2004/08/xop/include";

	private final XMLInputFactory xmlInputFactory;

	public XOPReconstitutor() {
		xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	public InputStream reconstituteIfNecessary(Message message) throws IOException {
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
	public Message getReconstitutedMessage(Message message) throws ParseException, IOException, EndOfStreamException, XMLStreamException, FactoryConfigurationError {
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
			protected void parseStartLine(InputStream in) {
				throw new RuntimeException("not implemented.");
			}

			@Override
			public String getStartLine() {
				throw new RuntimeException("not implemented.");
			}

			@Override
			public <T extends Message> T createSnapshot(Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) {
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
			throws IOException, EndOfStreamException {
		HashMap<String, Part> parts = new HashMap<>();

		MultipartStream multipartStream = new MultipartStream(MessageUtil.getContentAsStream(message), boundary.getBytes(UTF_8));
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

				if (event instanceof StartElement start) {
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
				} else if (event instanceof EndElement start) {
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
