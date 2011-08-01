package com.predic8.membrane.core.rules;

import javax.xml.stream.*;

import com.predic8.membrane.core.*;


public class ProxyRule extends AbstractRule {

	public ProxyRule() {}
	
	public ProxyRule(Router router) {
		setRouter(router);
	}
	
	public ProxyRule(ProxyRuleKey ruleKey) {
		super(ruleKey);
	}
	
	@Override
	protected void parseKeyAttributes(XMLStreamReader token) {
		key = new ProxyRuleKey(Integer.parseInt(token.getAttributeValue(Constants.NS_UNDEFINED, "port")));
	}
	
	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("proxy");
		
		writeRule(out);
		
		out.writeEndElement();
	}
	
}
