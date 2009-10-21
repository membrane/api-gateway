package com.predic8.membrane.core.rules;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;


public class ProxyRule extends AbstractRule {

	public static final String ELEMENT_NAME = "proxy-rule";
	
	public ProxyRule() {
		
	}
	
	public ProxyRule(ProxyRuleKey ruleKey) {
		super(ruleKey);
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		out.writeAttribute("name", name);
		
		out.writeAttribute("port", "" + ruleKey.getPort());
		
		out.writeEndElement();
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {

		name = token.getAttributeValue("", "name");

		int port = Integer.parseInt(token.getAttributeValue("", "port"));

		ruleKey = new ProxyRuleKey(port);
		
	}
	
}
