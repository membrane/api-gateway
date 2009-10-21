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

import com.predic8.membrane.core.config.TargetHost;
import com.predic8.membrane.core.config.TargetPort;

public class ForwardingRule extends AbstractRule implements Rule {

	public static final String ELEMENT_NAME = "forwarding-rule";

	private String targetHost;
	private String targetPort;

	public ForwardingRule() {

	}

	public ForwardingRule(ForwardingRuleKey ruleKey, String targetHost, String targetPort) {
		this.ruleKey = ruleKey;
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
		return Integer.parseInt(targetPort);
	}

	public void setTargetPort(String targetPort) {
		this.targetPort = targetPort;
	}

	public void setTargetPort(int targetPort) {
		this.targetPort = Integer.toString(targetPort);
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {

		name = token.getAttributeValue("", "name");
		
		String host = token.getAttributeValue("", "host");

		int port = Integer.parseInt(token.getAttributeValue("", "port"));

		String path = token.getAttributeValue("", "path");

		String method = token.getAttributeValue("", "method");

		ruleKey = new ForwardingRuleKey(host, method, path, port);
		
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {

		if (TargetPort.ELEMENT_NAME.equals(child)) {
			this.targetPort = ((TargetPort) (new TargetPort().parse(token))).getValue();
		} else if (TargetHost.ELEMENT_NAME.equals(child)) {
			this.targetHost = ((TargetHost) (new TargetHost().parse(token))).getValue();
		}

	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		out.writeAttribute("name", name);

		out.writeAttribute("host", ruleKey.getHost());
		
		out.writeAttribute("port", ""+ruleKey.getPort());
		
		out.writeAttribute("path", ruleKey.getPath());
		
		out.writeAttribute("method", ruleKey.getMethod());
		
		TargetPort childTargetPort = new TargetPort();
		childTargetPort.setValue(targetPort);
		childTargetPort.write(out);
		
		TargetHost childTargetHost = new TargetHost();
		childTargetHost.setValue(targetHost);
		childTargetHost.write(out);
		
		out.writeEndElement();
	}

}
