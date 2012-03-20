package com.predic8.membrane.core.multipart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.util.EndOfStreamException;

/**
 * Reassemble a multipart XOP message (see
 * http://en.wikipedia.org/wiki/XML-binary_Optimized_Packaging and
 * http://www.w3.org/TR/xop10/ ) into one stream (that can be used for schema
 * validation, for example).
 */
public class XOPReconstitutor {
	private static Log log = LogFactory.getLog(XOPReconstitutor.class.getName());
	private static final String XOP_NAMESPACE_URI = "http://www.w3.org/2004/08/xop/include";
	
	private final XMLInputFactory xmlInputFactory; 
	
	public XOPReconstitutor() {
		xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}
	
	public InputStream reconstituteIfNecessary(Message message) throws XMLStreamException {
		try {
			InputStream result = getSOAPStreamInternal(message);
			if (result != null)
				return result;
		} catch (Exception e) {
			log.warn(e);
			e.printStackTrace();
		}
		return message.getBodyAsStream();
	}
	
	private XMLEventReader createEventReaderFromStream(InputStream is) throws XMLStreamException {
		synchronized (xmlInputFactory) {
			return xmlInputFactory.createXMLEventReader(is);
		}
	}
	
	/**
	 * @return reassembled SOAP stream or null if message is not SOAP or not multipart
	 */
	private InputStream getSOAPStreamInternal(Message message) throws ParseException, MalformedStreamException, IOException, EndOfStreamException, XMLStreamException, FactoryConfigurationError {
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
					
		return fillInXOPParts(startPart.getInputStream(), parts);
	}

	@SuppressWarnings("deprecation")
	private HashMap<String, Part> split(Message message, String boundary)
			throws IOException, EndOfStreamException, MalformedStreamException {
		HashMap<String, Part> parts = new HashMap<String, Part>();
		
		MultipartStream multipartStream = new MultipartStream(message.getBodyAsStream(), boundary.getBytes(Constants.UTF_8_CHARSET));
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

	private InputStream fillInXOPParts(InputStream inputStream,
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
		return new ByteArrayInputStream(baos.toByteArray());
	}

}
