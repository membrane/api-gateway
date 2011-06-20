package com.predic8.membrane.core.rules;

import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.*;


public class ProxyRule extends AbstractRule {

	public static final String ELEMENT_NAME = "proxy-rule";
	
	public ProxyRule() {
		
	}
	
	public ProxyRule(Router router) {
		setRouter(router);
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
		key = new ProxyRuleKey(Integer.parseInt(token.getAttributeValue(Constants.NS_UNDEFINED, "port")));
	}
}
