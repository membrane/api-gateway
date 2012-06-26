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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;
import com.predic8.xml.util.ResourceDownloadException;

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
		key = new ServiceProxyKey(parseHost(token), "*", ".*", parsePort(token));
		wsdl = token.getAttributeValue("", "wsdl");
		if (token.getAttributeValue("", "method") != null)
			throw new RuntimeException("Attribute 'method' is not allowed on <soapServiceProxy />.");
		
		parseWSDL();
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

	@Override
	public void setRouter(Router router) {
		super.setRouter(router);
		
		parseWSDL();
	}
	
	private void parseWSDL() {
		if (router == null || wsdl == null)
			return;
		
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		try {
			WSDLParser wsdlParser = new WSDLParser();
			wsdlParser.setResourceResolver(router.getResourceResolver().toExternalResolver());
			List<Service> services = wsdlParser.parse(ctx).getServices();
			if (services.size() != 1)
				throw new IllegalArgumentException("There are " + services.size() + " services defined in the WSDL, but exactly 1 is required for soapServiceProxy.");
			List<Port> ports = services.get(0).getPorts();
			if (ports.size() != 1)
				throw new IllegalArgumentException("There are " + ports.size() + " ports defined in the WSDL, but exactly 1 is required for soapServiceProxy.");
			String location = ports.get(0).getAddress().getLocation();
			if (location == null)
				throw new IllegalArgumentException("In the WSDL, there is no @location defined on the port.");
			try {
				URL url = new URL(location);
				setTargetURL(location);
				key.setPathRegExp(true);
				key.setPath(Pattern.quote(url.getPath()) + "(|\\?wsdl|\\?WSDL|\\?xsd.*)");
				((ServiceProxyKey)key).setMethod("*");
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("WSDL endpoint location '"+location+"' is not an URL.");
			}
		} catch (ResourceDownloadException e) {
			throw new IllegalArgumentException("Could not download the WSDL '" + wsdl + "'.");
		}
	}

}
