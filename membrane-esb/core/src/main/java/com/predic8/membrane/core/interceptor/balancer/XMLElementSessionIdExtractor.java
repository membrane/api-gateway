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
package com.predic8.membrane.core.interceptor.balancer;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.FixedStreamReader;
import com.predic8.membrane.core.http.Message;

public class XMLElementSessionIdExtractor extends AbstractSessionIdExtractor {

	private static Log log = LogFactory.getLog(XMLElementSessionIdExtractor.class.getName());
	
	private String localName;
	private String namespace;
	private XMLInputFactory fac = XMLInputFactory.newInstance();
	
	@Override
	public String getSessionId(Message msg) throws Exception {
		if ( !msg.isXML() ) {
			log.debug("Didn't search a XML element in none XML message.");
			return null;
		}
		
		log.debug("searching for sessionid");
		
		fac.setProperty("javax.xml.stream.isNamespaceAware", namespace != null);
		XMLStreamReader reader = new FixedStreamReader(fac.createXMLStreamReader(msg.getBodyAsStream()));
		while ( reader.hasNext() ) {
			reader.next();
			if (isSessionIdElement(reader)) {
				log.debug("sessionid element found");
				return reader.getElementText();
			}
				
		}
			
		log.debug("no sessionid element found");
		return null;
	}

	private boolean isSessionIdElement(XMLStreamReader reader) {
		return reader.isStartElement() &&
			localName.equals(reader.getLocalName()) &&
			(namespace == null || namespace.equals(reader.getNamespaceURI()));
	}

	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("xmlSessionIdExtractor");

		out.writeAttribute("localName", localName);
		out.writeAttribute("namespace", namespace);

		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token)
			throws XMLStreamException {
		localName = token.getAttributeValue("", "localName");
		namespace = token.getAttributeValue("", "namespace");
	}
	
	@Override
	protected String getElementName() {
		return "sessionIdExtractor";
	}
		
	
}
