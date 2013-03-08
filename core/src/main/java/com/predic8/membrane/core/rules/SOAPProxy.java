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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptor;
import com.predic8.membrane.core.interceptor.soap.WebServiceExplorerInterceptor;
import com.predic8.membrane.core.util.URLUtil;
import com.predic8.membrane.core.ws.relocator.Relocator.PathRewriter;
import com.predic8.wsdl.AbstractBinding;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;
import com.predic8.xml.util.ResourceDownloadException;

public class SOAPProxy extends AbstractServiceProxy {
	private static final Log log = LogFactory.getLog(SOAPProxy.class.getName());
	public static final String ELEMENT_NAME = "soapProxy";
	private static final Pattern relativePathPattern = Pattern.compile("^./[^/?]*\\?");

	protected String wsdl;
	protected String portName;
	protected String targetPath;
	
	public SOAPProxy() {
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
		key = new ServiceProxyKey(parseHost(token), "*", ".*", parsePort(token), parseIp(token));
		name = token.getAttributeValue("", "name");
		wsdl = token.getAttributeValue("", "wsdl");
		portName = token.getAttributeValue("", "portName");
		if (token.getAttributeValue("", "method") != null)
			throw new RuntimeException("Attribute 'method' is not allowed on <soapProxy />.");
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {		
		if ("target".equals(child)) {
			// TODO: overwrite location extracted from WSDL
			throw new Exception("Child element <target> is not allowed on <soapProxy />.");
		} else if (Path.ELEMENT_NAME.equals(child)) {
			key.setUsePathPattern(true);
			Path p = (Path)(new Path()).parse(token);
			key.setPathRegExp(false);
			key.setPath(p.getValue());
			if (p.isRegExp())
				throw new Exception("A <path> element with a parent of <soapProxy> must not have @isRegExp='true'.");
		} else {
			super.parseChildren(token, child);
		}
	}

	@Override
	protected void writeExtension(XMLStreamWriter out) throws XMLStreamException {
		writeAttrIfTrue(out, !"*".equals(key.getHost()), "host", key.getHost());
		out.writeAttribute("wsdl", wsdl);
		if (name != null)
			out.writeAttribute("name", name);
		if (portName != null)
			out.writeAttribute("portName", portName);
		
		if (key.getPath() != null) {
			Path path = new Path();
			path.setValue(key.getPath());
			path.setRegExp(false);
			path.write(out);
		}
	}
	
	@Override
	protected void writeTarget(XMLStreamWriter out) throws XMLStreamException {
		// do nothing
	}
	
	private void parseWSDL(Router router) {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		try {
			WSDLParser wsdlParser = new WSDLParser();
			wsdlParser.setResourceResolver(router.getResourceResolver().toExternalResolver());
			
			Definitions definitions = wsdlParser.parse(ctx);
			
			List<Service> services = definitions.getServices();
			if (services.size() != 1)
				throw new IllegalArgumentException("There are " + services.size() + " services defined in the WSDL, but exactly 1 is required for soapProxy.");
			Service service = services.get(0);
			
			if (StringUtils.isEmpty(name))
				name = StringUtils.isEmpty(service.getName()) ? definitions.getName() : service.getName();
			
			List<Port> ports = service.getPorts();
			Port port = selectPort(ports, portName);
			
			String location = port.getAddress().getLocation();
			if (location == null)
				throw new IllegalArgumentException("In the WSDL, there is no @location defined on the port.");
			try {
				URL url = new URL(location);
				setTargetHost(url.getHost());
				if (url.getPort() != -1)
					setTargetPort(url.getPort());
				if (key.getPath() == null) {
					key.setUsePathPattern(true);
					key.setPathRegExp(true);
					key.setPath(Pattern.quote(url.getPath()) + ".*");
				} else {
					targetPath = url.getPath();
				}
				((ServiceProxyKey)key).setMethod("*");
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("WSDL endpoint location '"+location+"' is not an URL.");
			}
		} catch (ResourceDownloadException e) {
			throw new IllegalArgumentException("Could not download the WSDL '" + wsdl + "'.");
		}
	}
	
	public static Port selectPort(List<Port> ports, String portName) {
		if (portName != null) {
			for (Port port : ports)
				if (portName.equals(port.getName()))
					return port;
			throw new IllegalArgumentException("No port with name '" + portName + "' found.");
		}
		Port port = getPortByNamespace(ports, Constants.WSDL_SOAP11_NS);
		if (port == null)
			port = getPortByNamespace(ports, Constants.WSDL_SOAP12_NS);
		if (port == null)
			throw new IllegalArgumentException("No SOAP/1.1 or SOAP/1.2 ports found in WSDL.");
		return port;
	}
	
	private static Port getPortByNamespace(List<Port> ports, String namespace) {
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

	private int automaticallyAddedInterceptorCount;

	@Override
	public void init(Router router) throws Exception {
		if (wsdl == null)
			return;

		// remove previously added interceptors
		for(; automaticallyAddedInterceptorCount > 0; automaticallyAddedInterceptorCount--)
			interceptors.remove(0);

		parseWSDL(router);

		// add interceptors (in reverse order) to position 0.
		
		WebServiceExplorerInterceptor sui = new WebServiceExplorerInterceptor();
		sui.setWsdl(wsdl);
		sui.setPortName(portName);
		interceptors.add(0, sui);
		automaticallyAddedInterceptorCount++;

		if (!containsInterceptorOfType(WSDLPublisherInterceptor.class)) {
			WSDLPublisherInterceptor wp = new WSDLPublisherInterceptor();
			wp.setWsdl(wsdl);
			interceptors.add(0, wp);
			automaticallyAddedInterceptorCount++;
		}

		if (!containsInterceptorOfType(WSDLInterceptor.class)) {
			WSDLInterceptor wp = new WSDLInterceptor();
			if (key.getPath() != null) {
				wp.setPathRewriter(new PathRewriter() {
					@Override
					public String rewrite(String path2) {
						try {
							if (path2.contains("://")) {
								path2 = new URL(new URL(path2), key.getPath()).toString();
							} else {
								Matcher m = relativePathPattern.matcher(path2);
								path2 = m.replaceAll("./" + URLUtil.getName(key.getPath()) + "?");
							}
						} catch (MalformedURLException e) {
						}
						return path2;
					}
				});
			}
			interceptors.add(0, wp);
			automaticallyAddedInterceptorCount++;
		}
		
		if (key.getPath() != null) {
			RewriteInterceptor ri = new RewriteInterceptor();
			ri.setMappings(Lists.newArrayList(new RewriteInterceptor.Mapping("^" + Pattern.quote(key.getPath()), Matcher.quoteReplacement(targetPath), "rewrite")));
			interceptors.add(0, ri);
			automaticallyAddedInterceptorCount++;
		}
		
		super.init(router);
	}

	private boolean containsInterceptorOfType(Class<? extends Interceptor> class1) {
		for (Interceptor i : interceptors)
			if (class1.isInstance(i))
				return true;
		return false;
	}

	protected void writeInterceptors(XMLStreamWriter out)
			throws XMLStreamException {
		if (interceptors.size() > automaticallyAddedInterceptorCount)
			writeInterceptors(out, interceptors.subList(automaticallyAddedInterceptorCount, interceptors.size()));
	}

	public String getWsdl() {
		return wsdl;
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}
	
	public String getPortName() {
		return portName;
	}
	
	public void setPortName(String portName) {
		this.portName = portName;
	}

}
