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

package com.predic8.membrane.core.rules;

import javax.xml.stream.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.*;

public class ServiceProxy extends AbstractRule {

	public static final String ELEMENT_NAME = "serviceProxy";
	
	private String targetHost;
	private int targetPort;
	private String targetURL;
	
	public ServiceProxy() {}

	public ServiceProxy(Router router) {
		setRouter(router);
	}

	public ServiceProxy(ForwardingRuleKey ruleKey, String targetHost, int targetPort) {
		this(ruleKey, targetHost, targetPort, false, false);
	}

	public ServiceProxy(ForwardingRuleKey ruleKey, String targetHost, int targetPort, boolean inboundTLS, boolean outboundTLS) {
		this.key = ruleKey;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.inboundTLS = inboundTLS;
		this.outboundTLS = outboundTLS;
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
		String host = defaultTo(token.getAttributeValue(Constants.NS_UNDEFINED, "host"), "*");
		int port = Integer.parseInt(defaultTo(token.getAttributeValue(Constants.NS_UNDEFINED, "port"),"80"));
		String method = defaultTo(token.getAttributeValue(Constants.NS_UNDEFINED, "method"), "*");
		key = new ForwardingRuleKey(host, method, ".*", port);
	}
	
	private String defaultTo(String value, String default_) {
		if (value == null)
			return default_;
		
		return value;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {		
		if ("target".equals(child)) {
			GenericConfigElement target = new GenericConfigElement();
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
		out.writeStartElement("target");
		
		out.writeAttribute("host", targetHost);
		out.writeAttribute("port", ""+targetPort);
		if (targetURL!=null)
			out.writeAttribute("url", targetURL);
		
		out.writeEndElement();
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
}
