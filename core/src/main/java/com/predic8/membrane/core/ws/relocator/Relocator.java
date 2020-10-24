/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.ws.relocator;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.predic8.membrane.core.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotThreadSafe
public class Relocator {
	private static Logger log = LoggerFactory.getLogger(Relocator.class.getName());

	private final XMLEventFactory fac = XMLEventFactory.newInstance();

	private final String host;
	private final int port;
	private final String contextPath;
	private final String protocol;
	private final XMLEventWriter writer;
	private final PathRewriter pathRewriter;

	private Map<QName, String> relocatingAttributes = new HashMap<QName, String>();

	private boolean wsdlFound;

	private class ReplaceIterator implements Iterator<Attribute> {

		private final XMLEventFactory fac;
		private final Iterator<Attribute> attrs;
		private final String replace;

		public ReplaceIterator(XMLEventFactory fac, String replace, Iterator<Attribute> attrs) {
			this.fac = fac;
			this.replace = replace;
			this.attrs = attrs;
		}

		public boolean hasNext() {
			return attrs.hasNext();
		}

		public Attribute next() {
			Attribute atr = attrs.next();
			if (atr.getName().equals(new QName(replace))) {
				String value = atr.getValue();
				if (pathRewriter != null) {
					value = pathRewriter.rewrite(value);
					if (value.startsWith("http"))
						return fac.createAttribute(replace, getNewLocation(value, protocol, host, port, contextPath));
					return fac.createAttribute(replace, value);
				}
				if (value.startsWith("http"))

					return fac.createAttribute(replace, getNewLocation(value, protocol, host, port, contextPath));
			}
			return atr;
		}

		public void remove() {
			attrs.remove();
		}
	}

	public interface PathRewriter {
		String rewrite(String path);
	}

	public Relocator(Writer w, String protocol, String host, int port, String contextPath, PathRewriter pathRewriter)
			throws Exception {
		this.writer = XMLOutputFactory.newInstance().createXMLEventWriter(w);
		this.host = host;
		this.port = port;
		this.protocol = protocol;
		this.contextPath = contextPath;
		this.pathRewriter = pathRewriter;
	}

	public Relocator(OutputStreamWriter osw, String protocol, String host,
					 int port, String contextPath, PathRewriter pathRewriter) throws Exception {
		this.writer = XMLOutputFactory.newInstance().createXMLEventWriter(osw);
		this.host = host;
		this.port = port;
		this.protocol = protocol;
		this.contextPath = contextPath;
		this.pathRewriter = pathRewriter;
	}

	public void relocate(InputStreamReader isr) throws Exception {
		XMLEventReader parser = XMLInputFactory.newInstance()
				.createXMLEventReader(isr);

		while (parser.hasNext()) {
			writer.add(getEvent(parser));
		}
		writer.flush();
	}

	private XMLEvent getEvent(XMLEventReader parser) throws XMLStreamException {
		XMLEvent event = parser.nextEvent();
		if (!event.isStartElement())
			return event;

		/*
		 * if (isAddressElement(event)) { return replace(event, "location"); }
		 * else if (getElementName(event).equals(INCLUDE)) { return
		 * replace(event, "schemaLocation"); } else if
		 * (getElementName(event).equals(IMPORT)) { return replace(event,
		 * "schemaLocation"); } else if
		 * (getElementName(event).equals(WADL_RESOURCES)) { return
		 * replace(event, "base"); } else if
		 * (getElementName(event).equals(WADL_INCLUDE)) { return replace(event,
		 * "href"); } else if (getElementName(event).getNamespaceURI().equals(
		 * Constants.WSDL_SOAP11_NS) ||
		 * getElementName(event).getNamespaceURI().equals(
		 * Constants.WSDL_SOAP12_NS)) { wsdlFound = true; }
		 */

		if (getElementName(event).getNamespaceURI().equals(
				Constants.WSDL_SOAP11_NS)
				|| getElementName(event).getNamespaceURI().equals(
						Constants.WSDL_SOAP12_NS)) {
			wsdlFound = true;
		}

		for (QName qn : relocatingAttributes.keySet()) {
			if (getElementName(event).equals(qn)) {
				return replace(event, relocatingAttributes.get(qn));
			}
		}

		return event;
	}

	private QName getElementName(XMLEvent event) {
		return event.asStartElement().getName();
	}

	@SuppressWarnings("unchecked")
	private XMLEvent replace(XMLEvent event, String attribute) {
		StartElement startElement = event.asStartElement();
		return fac.createStartElement(startElement.getName(),
				new ReplaceIterator(fac, attribute, startElement.getAttributes()),
				startElement.getNamespaces());
	}

	public boolean isWsdlFound() {
		return wsdlFound;
	}

	public static String getNewLocation(String addr, String protocol,
			String host, int port, String contextPath) {
		try {
			URL oldURL = new URL(addr);
			if (port == -1) {
				return new URL(protocol, host, contextPath + oldURL.getFile()).toString();
			}
			if ("http".equals(protocol) && port == 80)
				port = -1;
			if ("https".equals(protocol) && port == 443)
				port = -1;
			return new URL(protocol, host, port, contextPath + oldURL.getFile()).toString();
		} catch (MalformedURLException e) {
			log.error("", e);
		}
		return "";
	}

	public Map<QName, String> getRelocatingAttributes() {
		return relocatingAttributes;
	}

	public void setRelocatingAttributes(Map<QName, String> relocatingAttributes) {
		this.relocatingAttributes = relocatingAttributes;
	}

}
