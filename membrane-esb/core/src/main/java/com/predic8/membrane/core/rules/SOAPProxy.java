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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptor;
import com.predic8.membrane.core.interceptor.soap.SOAPUIInterceptor;
import com.predic8.wsdl.AbstractBinding;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;
import com.predic8.xml.util.ResourceDownloadException;

public class SOAPProxy extends ServiceProxy {
	private static final Log log = LogFactory.getLog(SOAPProxy.class.getName());
	public static final String ELEMENT_NAME = "soapProxy";

	protected String wsdl;
	private boolean inited;
	
	public SOAPProxy() {
	}
	
	public SOAPProxy(Router router) {
		super(router);
	}
	
	@Override
	protected AbstractProxy getNewInstance() {
		return new SOAPProxy();
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
			throw new RuntimeException("Attribute 'method' is not allowed on <soapProxy />.");
		
		parseWSDL();
		init();
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {		
		if ("target".equals(child)) {
			// TODO: overwrite location extracted from WSDL
			throw new Exception("Child element <target> is not allowed on <soapProxy />.");
		} else if (Path.ELEMENT_NAME.equals(child)) {
			// TODO: overwrite path extracted from WSDL (rewriting?)
			throw new Exception("Child element <path> is not allowed on <soapProxy />.");
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
		Router old = router;
		super.setRouter(router);
		if (old != router) {
			parseWSDL();
			init();
		}
	}
	
	private void parseWSDL() {
		if (router == null || wsdl == null)
			return;
		
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		try {
			WSDLParser wsdlParser = new WSDLParser();
			wsdlParser.setResourceResolver(router.getResourceResolver().toExternalResolver());
			
			Definitions definitions = wsdlParser.parse(ctx);
			
			if (StringUtils.isEmpty(name))
				name = definitions.getName();
				
			List<Service> services = definitions.getServices();
			if (services.size() != 1)
				throw new IllegalArgumentException("There are " + services.size() + " services defined in the WSDL, but exactly 1 is required for soapProxy.");
			List<Port> ports = services.get(0).getPorts();

			Port port = getPortByNamespace(ports, Constants.WSDL_SOAP11_NS);
			if (port == null)
				port = getPortByNamespace(ports, Constants.WSDL_SOAP12_NS);
			if (port == null)
				throw new IllegalArgumentException("No SOAP/1.1 or SOAP/1.2 ports found in WSDL.");
			
			String location = port.getAddress().getLocation();
			if (location == null)
				throw new IllegalArgumentException("In the WSDL, there is no @location defined on the port.");
			try {
				URL url = new URL(location);
				setTargetURL(location);
				key.setUsePathPattern(true);
				key.setPathRegExp(true);
				key.setPath(Pattern.quote(url.getPath()) + "(|\\?[wW][sS][dD][lL]|\\?xsd.*)");
				((ServiceProxyKey)key).setMethod("*");
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("WSDL endpoint location '"+location+"' is not an URL.");
			}
		} catch (ResourceDownloadException e) {
			throw new IllegalArgumentException("Could not download the WSDL '" + wsdl + "'.");
		}
	}
	
	private Port getPortByNamespace(List<Port> ports, String namespace) {
		for (Port port : ports) {
			try {
				if (port.getBinding() == null)
					continue;
				if (port.getBinding().getBinding() == null)
					continue;
				AbstractBinding binding = port.getBinding().getBinding();
				if (!"http://schemas.xmlsoap.org/soap/http".equals(binding.getProperty("transport")))
					continue;
				if (!namespace.equals(binding.getElementName().getNamespaceURI()))
					continue;
				return port;
			} catch (Exception e) {
				log.warn("Error inspecting WSDL port binding.", e);
			}
		}
		return null;
	}
	
	private void init() {
		if (router == null || wsdl == null)
			return;
		if (inited) {
			interceptors.remove(0);
			interceptors.remove(1);
		}
		inited = true;

		WSDLPublisherInterceptor wp = new WSDLPublisherInterceptor();
		wp.setWsdl(wsdl);
		wp.setRouter(router);
		interceptors.add(0, wp);

		SOAPUIInterceptor sui = new SOAPUIInterceptor();
		sui.setWsdl(wsdl);
		sui.setRouter(router);
		interceptors.add(1, sui);
	}

	protected void writeInterceptors(XMLStreamWriter out)
			throws XMLStreamException {
		if (interceptors.size() > 2)
			writeInterceptors(out, interceptors.subList(2, interceptors.size()));
	}

}
