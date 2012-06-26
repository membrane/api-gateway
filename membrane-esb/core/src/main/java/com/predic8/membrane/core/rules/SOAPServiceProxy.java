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
package com.predic8.membrane.core.rules;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;

public class SOAPServiceProxy extends ServiceProxy {
	
	public static final String ELEMENT_NAME = "soapServiceProxy";

	protected String wsdl;
	
	public SOAPServiceProxy() {
	}
	
	public SOAPServiceProxy(Router router) {
		super(router);
	}
	
	@Override
	protected AbstractProxy getNewInstance() {
		return new SOAPServiceProxy();
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		wsdl = token.getAttributeValue("", "wsdl");
		if (token.getAttributeValue("", "method") != null)
			throw new RuntimeException("Attribute 'method' is not allowed on <soapServiceProxy />.");
		// TODO: setTargetURL
		// TODO: adjust key (path, method)
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {		
		if ("target".equals(child)) {
			throw new Exception("Child element <target> is not allowed on <soapServiceProxy />.");
		} else if (Path.ELEMENT_NAME.equals(child)) {
			throw new Exception("Child element <path> is not allowed on <soapServiceProxy />.");
		} else {
			super.parseChildren(token, child);
		}
	}

	@Override
	protected void writeExtension(XMLStreamWriter out) throws XMLStreamException {
		writeAttrIfTrue(out, !"*".equals(key.getHost()), "host", key.getHost());
		out.writeAttribute("wsdl", wsdl);
	}
	
	@Override
	protected void writeTarget(XMLStreamWriter out) throws XMLStreamException {
		// do nothing
	}

}
