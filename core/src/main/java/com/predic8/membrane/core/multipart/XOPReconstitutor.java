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

import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.MessageUtil;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

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

	public InputStream reconstituteIfNecessary(Message message) {
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

		if(message == null)
			return null;

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

		HashMap<String, Part> parts = splitById(message, boundary);
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

			@Override
			public boolean shouldNotContainBody() {
				return false;
			}
		};
		m.setBodyContent(body);

		String reconstitutedContentType = innerContentType.getParameter("type");
		if (reconstitutedContentType != null)
			m.getHeader().add(Header.CONTENT_TYPE, reconstitutedContentType);

		return m;
	}

	/** Splits the multipart message and indexes parts by Content-ID for XOP lookup. */
	private HashMap<String, Part> splitById(Message message, String boundary) throws IOException {
		HashMap<String, Part> byId = new HashMap<>();
		for (Part part : MultipartUtil.split(message, boundary)) {
			String id = part.getContentID();
			if (id != null) {
				byId.put(id, part);
			}
		}
		return byId;
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

						writer.add(base64CharactersEvent(p.getBody()));
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

	/** Wraps raw bytes as a base64-encoded XML Characters event for XOP inlining. */
	private static Characters base64CharactersEvent(byte[] data) {
		// Encode once up front: getData() may be called repeatedly by the XMLEventWriter,
		// and re-encoding on each call would needlessly repeat the work for large parts.
		String encoded = new String(encodeBase64(data), UTF_8);
		return new Characters() {
			@Override public String getData()             { return encoded; }
			@Override public boolean isCharacters()       { return true; }
			@Override public boolean isWhiteSpace()       { return false; }
			@Override public boolean isCData()            { return false; }
			@Override public boolean isIgnorableWhiteSpace() { return false; }
			@Override public int getEventType()           { return CHARACTERS; }
			@Override public Characters asCharacters()    { return this; }
			@Override public boolean isStartElement()     { return false; }
			@Override public boolean isEndElement()       { return false; }
			@Override public boolean isStartDocument()    { return false; }
			@Override public boolean isEndDocument()      { return false; }
			@Override public boolean isAttribute()        { return false; }
			@Override public boolean isNamespace()        { return false; }
			@Override public boolean isEntityReference()  { return false; }
			@Override public boolean isProcessingInstruction() { return false; }
			@Override public QName getSchemaType()        { return null; }
			@Override public Location getLocation()       { return null; }
			@Override public StartElement asStartElement() { return null; }
			@Override public EndElement asEndElement()    { return null; }
			@Override public void writeAsEncodedUnicode(Writer writer) {
				throw new UnsupportedOperationException();
			}
		};
	}

}
