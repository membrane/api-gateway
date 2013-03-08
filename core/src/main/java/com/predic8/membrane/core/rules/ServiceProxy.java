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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.GenericComplexElement;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.SSLContext;

public class ServiceProxy extends AbstractServiceProxy {

	public static final String ELEMENT_NAME = "serviceProxy";
	
	private SSLParser sslOutboundParser;
	
	public ServiceProxy() {}

	public ServiceProxy(ServiceProxyKey ruleKey, String targetHost, int targetPort) {
		this.key = ruleKey;
		setTargetHost(targetHost);
		setTargetPort(targetPort);
	}
	

	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		key = new ServiceProxyKey(parseHost(token), parseMethod(token), ".*", parsePort(token), parseIp(token));
	}

	private String parseMethod(XMLStreamReader token) {
		return defaultString(token.getAttributeValue("", "method"), "*");
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
						sslOutboundParser = new SSLParser();
						sslOutboundParser.parse(token);
					} else {
						super.parseChildren(token, child);
					}
				}
			});
			target.parse(token);
			setTargetHost(target.getAttribute("host"));
			setTargetPort(Integer.parseInt(target.getAttributeOrDefault("port","80")));			
			setTargetURL(target.getAttribute("service")!=null?
						"service:"+target.getAttribute("service"):
						target.getAttribute("url"));
		} else if (Path.ELEMENT_NAME.equals(child)) {
			key.setUsePathPattern(true);
			Path p = (Path)(new Path()).parse(token);
			key.setPathRegExp(p.isRegExp());
			key.setPath(p.getValue());
		} else {
			super.parseChildren(token, child);
		}
	}
	

	@Override
	protected void writeExtension(XMLStreamWriter out) throws XMLStreamException {
		writeAttrIfTrue(out, !"*".equals(key.getHost()), "host", key.getHost());		
		writeAttrIfTrue(out, !"*".equals(key.getMethod()), "method", key.getMethod());		

		if (key.isUsePathPattern()) {
			Path path = new Path();
			path.setValue(key.getPath());
			path.setRegExp(key.isPathRegExp());
			path.write(out);
		}
	}

	protected void writeTarget(XMLStreamWriter out) throws XMLStreamException {
		if (getTargetHost() == null && getTargetPort() == 0 && getTargetURL() == null)
			return;
		
		out.writeStartElement("target");
		
		if (getTargetHost() != null)
			out.writeAttribute("host", getTargetHost());
		
		if (getTargetPort() != 0 && getTargetPort() != 80)
			out.writeAttribute("port", "" + getTargetPort());
		
		if (getTargetURL() != null)
			out.writeAttribute("url", getTargetURL());

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
	
	public void setSslOutboundParser(SSLParser sslOutboundParser) {
		this.sslOutboundParser = sslOutboundParser;
	}
	
	@Override
	public void init(Router router) throws Exception {
		super.init(router);
		if (sslOutboundParser != null)
			setSslOutboundContext(new SSLContext(sslOutboundParser, router.getResourceResolver()));
	}
}
