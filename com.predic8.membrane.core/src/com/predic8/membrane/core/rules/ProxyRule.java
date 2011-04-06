package com.predic8.membrane.core.rules;

import javax.xml.stream.XMLStreamReader;


public class ProxyRule extends AbstractRule {

	public static final String ELEMENT_NAME = "proxy-rule";
	
	public ProxyRule() {
		
	}
	
	public ProxyRule(ProxyRuleKey ruleKey) {
		super(ruleKey);
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		int port = Integer.parseInt(token.getAttributeValue("", "port"));
		key = new ProxyRuleKey(port);
	}
}
