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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
	private static Log log = LogFactory.getLog(XMLProtector.class.getName());
	
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
		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

		try {
			XMLEventReader parser = xmlInputFactory.createXMLEventReader(isr);

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
