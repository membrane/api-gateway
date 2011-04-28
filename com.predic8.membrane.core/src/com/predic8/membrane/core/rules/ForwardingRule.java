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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.config.TargetHost;
import com.predic8.membrane.core.config.TargetPort;

public class ForwardingRule extends AbstractRule {

	public static final String ELEMENT_NAME = "forwarding-rule";

	private String targetHost;
	private int targetPort;

	public ForwardingRule() {
		
	}

	public ForwardingRule(ForwardingRuleKey ruleKey, String targetHost, int targetPort) {
		this(ruleKey, targetHost, targetPort, false, false);
	}

	public ForwardingRule(ForwardingRuleKey ruleKey, String targetHost, int targetPort, boolean inboundTLS, boolean outboundTLS) {
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

	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		String host = token.getAttributeValue("", "host");
		int port = Integer.parseInt(token.getAttributeValue("", "port"));
		String method = token.getAttributeValue("", "method");
		key = new ForwardingRuleKey(host, method, ".*", port);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		super.parseChildren(token, child);
		
		if (TargetPort.ELEMENT_NAME.equals(child)) {
			this.targetPort = Integer.parseInt(((TargetPort) (new TargetPort().parse(token))).getValue());
		} else if (TargetHost.ELEMENT_NAME.equals(child)) {
			this.targetHost = ((TargetHost) (new TargetHost().parse(token))).getValue();
		} 
		
		if (Path.ELEMENT_NAME.equals(child)) {
			key.setUsePathPattern(true);
			Path p = (Path)(new Path()).parse(token);
			key.setPathRegExp(p.isRegExp());
			key.setPath(p.getValue());
		}
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void writeExtension(XMLStreamWriter out) throws XMLStreamException {
		out.writeAttribute("host", key.getHost());
		out.writeAttribute("method", key.getMethod());

		new TargetPort("" + targetPort).write(out);
		new TargetHost(targetHost).write(out);
		
		if (key.isUsePathPattern()) {
			Path path = new Path();
			path.setValue(key.getPath());
			path.setRegExp(key.isPathRegExp());
			path.write(out);
		}
	}
	
}
