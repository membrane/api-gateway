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

package com.predic8.membrane.core.rules;

import static org.apache.commons.lang.StringUtils.defaultString;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.GenericComplexElement;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.SSLContext;

public class ServiceProxy extends AbstractProxy {

	public static final String ELEMENT_NAME = "serviceProxy";
	
	private String targetHost;
	private int targetPort;
	private String targetURL;
	private SSLContext sslInboundContext, sslOutboundContext;
	
	public ServiceProxy() {}

	public ServiceProxy(Router router) {
		setRouter(router);
	}

	public ServiceProxy(ServiceProxyKey ruleKey, String targetHost, int targetPort) {
		this.key = ruleKey;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
	}
	
	public String getTargetHost() {
		return targetHost;
	}

	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}

	public int getTargetPort() {
		return targetPort;
	}

	public void setTargetPort(int targetPort) {
		this.targetPort = targetPort;
	}

	public String getTargetURL() {
		return targetURL;
	}

	public void setTargetURL(String targetURL) {
		this.targetURL = targetURL;
	}

	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		key = new ServiceProxyKey(parseHost(token), parseMethod(token), ".*", parsePort(token));
	}

	private String parseMethod(XMLStreamReader token) {
		return defaultString(token.getAttributeValue("", "method"), "*");
	}

	private int parsePort(XMLStreamReader token) {
		return Integer.parseInt(defaultString(token.getAttributeValue("", "port"),"80"));
	}

	private String parseHost(XMLStreamReader token) {
		return defaultString(token.getAttributeValue("", "host"), "*");
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {		
		if ("target".equals(child)) {
			GenericComplexElement target = new GenericComplexElement();
			target.setChildParser(new AbstractXmlElement() {
				@Override
				protected void parseChildren(XMLStreamReader token, String child)
						throws Exception {
					if ("ssl".equals(child)) {
						SSLParser sslOutboundParser = new SSLParser(router);
						sslOutboundParser.parse(token);
						sslOutboundContext = new SSLContext(sslOutboundParser, router.getResourceResolver());
					} else {
						super.parseChildren(token, child);
					}
				}
			});
			target.parse(token);
			targetHost = target.getAttribute("host");
			targetPort = Integer.parseInt(target.getAttributeOrDefault("port","80"));			
			targetURL = target.getAttribute("service")!=null?
						"service:"+target.getAttribute("service"):
						target.getAttribute("url");
		} else if (Path.ELEMENT_NAME.equals(child)) {
			key.setUsePathPattern(true);
			Path p = (Path)(new Path(router)).parse(token);
			key.setPathRegExp(p.isRegExp());
			key.setPath(p.getValue());
		} else if ("ssl".equals(child)) {
			SSLParser sslInboundParser = new SSLParser(router);
			sslInboundParser.parse(token);
			sslInboundContext = new SSLContext(sslInboundParser, router.getResourceResolver());
		} else {
			super.parseChildren(token, child);
		}
	}
	
	
	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement(getElementName());
		
		writeRule(out);
		
		writeTarget(out);
		out.writeEndElement();
	}

	@Override
	protected void writeExtension(XMLStreamWriter out) throws XMLStreamException {
		writeAttrIfTrue(out, !"*".equals(key.getHost()), "host", key.getHost());		
		writeAttrIfTrue(out, !"*".equals(key.getMethod()), "method", key.getMethod());		

		if (key.isUsePathPattern()) {
			Path path = new Path(router);
			path.setValue(key.getPath());
			path.setRegExp(key.isPathRegExp());
			path.write(out);
		}
	}

	private void writeTarget(XMLStreamWriter out) throws XMLStreamException {
		if (targetHost == null && targetPort == 0 && targetURL == null)
			return;
		
		out.writeStartElement("target");
		
		if (targetHost != null)
			out.writeAttribute("host", targetHost);
		
		if (targetPort != 0 && targetPort != 80)
			out.writeAttribute("port", "" + targetPort);
		
		if (targetURL != null)
			out.writeAttribute("url", targetURL);

		out.writeEndElement();
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected AbstractProxy getNewInstance() {
		return new ServiceProxy();
	}
	
	@Override
	public String getName() {
		return StringUtils.defaultIfEmpty(name, getKey().toString());
	}

	@Override
	public SSLContext getSslInboundContext() {
		return sslInboundContext;
	}
	
	@Override
	public SSLContext getSslOutboundContext() {
		return sslOutboundContext;
	}
}
