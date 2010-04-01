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

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;

public class Proxy extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "proxy";

	public Map<String, Object> values = new HashMap<String, Object>();

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		values.put(Configuration.PROXY_USE, "true".equals(token.getAttributeValue("", "active")) ? true: false);
		super.parseAttributes(token);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (ProxyHost.ELEMENT_NAME.equals(child)) {
			String value = ((ProxyHost)(new ProxyHost().parse(token))).getValue();
			values.put(Configuration.PROXY_HOST, value);
		} else if (ProxyPort.ELEMENT_NAME.equals(child)) {
			String value = ((ProxyPort)(new ProxyPort().parse(token))).getValue();
			values.put(Configuration.PROXY_PORT, value);
		}
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> newValues) {
		if (newValues.containsKey(Configuration.PROXY_HOST)) {
			values.put(Configuration.PROXY_HOST, newValues.get(Configuration.PROXY_HOST));
		} 
		
		if (newValues.containsKey(Configuration.PROXY_PORT)) {
			values.put(Configuration.PROXY_PORT, newValues.get(Configuration.PROXY_PORT));
		}
		
		if (newValues.containsKey(Configuration.PROXY_USE)) {
			values.put(Configuration.PROXY_USE, newValues.get(Configuration.PROXY_USE));	
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
				
		out.writeAttribute("active", ((Boolean) (values.get(Configuration.PROXY_USE) == null ? false : (Boolean)values.get(Configuration.PROXY_USE))).toString());
		
		ProxyHost proxyHost = new ProxyHost();
		if (values.containsKey(Configuration.PROXY_HOST)) {
			proxyHost.setValue((String)values.get(Configuration.PROXY_HOST));
		} else {
			proxyHost.setValue("");
		}
		proxyHost.write(out);
		
		ProxyPort proxyPort = new ProxyPort();
		if (values.containsKey(Configuration.PROXY_PORT)) {
			proxyPort.setValue((String)values.get(Configuration.PROXY_PORT));
		} else {
			proxyPort.setValue("");
		}
		
		proxyPort.write(out);
		
		out.writeEndElement();
	}
	
}
