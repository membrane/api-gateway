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

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="proxy", group="rule")
public class ProxyRule extends AbstractProxy {

	public static final String ELEMENT_NAME = "proxy";
	
	public ProxyRule() {
		key = new ProxyRuleKey(80);
	}
	
	public ProxyRule(ProxyRuleKey ruleKey) {
		super(ruleKey);
	}
	
	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		key = new ProxyRuleKey(Integer.parseInt(token.getAttributeValue("", "port")), parseIp(token));
	}

	protected String parseIp(XMLStreamReader token) {
		return token.getAttributeValue("", "ip");
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement(getElementName());
		
		writeRule(out);
		
		out.writeEndElement();
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected AbstractProxy getNewInstance() {
		return new ProxyRule();
	}
	
	@Override
	public String getName() {
		return StringUtils.defaultIfEmpty(name, getKey().toString());
	}
	
	public int getPort() {
		return ((ProxyRuleKey)key).getPort();
	}
	
	@MCAttribute
	public void setPort(int port) {
		((ProxyRuleKey)key).setPort(port);
	}
	
	public String getIp() {
		return ((ProxyRuleKey)key).getIp();
	}
	
	@MCAttribute
	public void setIp(String ip) {
		((ProxyRuleKey)key).setIp(ip);
	}
}
