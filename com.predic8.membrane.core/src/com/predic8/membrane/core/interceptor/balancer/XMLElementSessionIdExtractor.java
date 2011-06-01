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
package com.predic8.membrane.core.interceptor.balancer;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.*;

public class XMLElementSessionIdExtractor {

	private static Log log = LogFactory.getLog(XMLElementSessionIdExtractor.class.getName());
	
	private String localName;
	private String namespace;
	private XMLInputFactory fac = XMLInputFactory.newInstance();
	
	public boolean hasSessionId(Message msg) throws Exception {
		return getSessionId(msg) != null;
	}
		
	public String getSessionId(Message msg) throws Exception {
		log.debug("searching for sessionid");
		
		XMLStreamReader reader = fac.createXMLStreamReader(msg.getBodyAsStream());
		
		while ( reader.hasNext() ) {
			reader.next();
			if (isSessionIdElement(reader)) {
				log.debug("sessionid element found");
				return reader.getElementText();
			}
				
		}
			
		log.warn("no sessionid element found");
		return null;
	}

	private boolean isSessionIdElement(XMLStreamReader reader) {
		return reader.isStartElement() &&
			localName.equals(reader.getLocalName()) &&
			namespace.equals(reader.getNamespaceURI());
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
		
	
}
