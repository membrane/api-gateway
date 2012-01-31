package com.predic8.membrane.core.rules;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;


public class ProxyRule extends AbstractProxy {

	public static final String ELEMENT_NAME = "proxy";
	
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
}
