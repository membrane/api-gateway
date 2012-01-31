/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.FixedStreamReader;

public abstract class AbstractXmlElement implements XMLElement {

	private static Log log = LogFactory.getLog(AbstractXmlElement.class
			.getName());

	/**
	 * Needed to resolve interceptor IDs into interceptor beans
	 */

	public XMLElement parse(XMLStreamReader token) throws Exception {
		move2RootElementIfNeeded(token);
		log.debug("<" + token.getLocalName() + ">");
		parseAttributes(token);
		while (token.hasNext()) {
			token.next();
			if (token.isStartElement()) {
				parseChildren(token, token.getName().getLocalPart());
			} else if (token.isCharacters()) {
				parseCharacters(token);
			} else if (token.isEndElement()) {
				log.debug("</" + token.getLocalName() + ">");
				break;
			}
		}
		doAfterParsing();
		return this;
	}

	protected void doAfterParsing() throws Exception {
	}

	protected void move2RootElementIfNeeded(XMLStreamReader token)
			throws XMLStreamException {
		if (token.getEventType() == XMLStreamReader.START_DOCUMENT) {
			while (!token.isStartElement()) {
				token.next();
			}
		}

	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
	}

	protected void parseAttributes(XMLStreamReader token) throws Exception {

	}

	protected void parseCharacters(XMLStreamReader token)
			throws XMLStreamException {

	}

	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		int count = 0;
		while (true) { // ignore child
			token.next();
			if (token.isEndElement()
					&& child.equals(token.getName().getLocalPart())) {
				if (count == 0)
					return;
				count--;
			} else if (token.isStartElement()
					&& child.equals(token.getName().getLocalPart())) {
				count++;
			}
		}
	}

	protected String getElementName() {
		return null;
	};

	public String toXml() throws Exception {
		StringWriter sw = new StringWriter();
		XMLStreamWriter w = XMLOutputFactory.newInstance()
				.createXMLStreamWriter(sw);
		w.writeStartDocument();
		write(w);
		w.writeEndDocument();
		return sw.toString();
	}

	protected boolean getBoolean(XMLStreamReader token, String attr) {
		return "true".equals(token.getAttributeValue("",
				attr));
	}

	protected void writeIfNotNull(AbstractXmlElement e, XMLStreamWriter out)
			throws XMLStreamException {
		if (e != null)
			e.write(out);
	}

}
