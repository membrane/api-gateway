package com.predic8.membrane.core.rules;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.Interceptors;


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
		
		out.writeAttribute("port", "" + key.getPort());
		
		out.writeAttribute("inboundTLS", Boolean.toString(inboundTSL));
		
		out.writeAttribute("outboundTLS", Boolean.toString(outboundTSL));
		
		Interceptors inters = new Interceptors();
		inters.setInterceptors(interceptors);
		inters.write(out);
		
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

		key = new ProxyRuleKey(port);
		
		inboundTSL = "true".equals(token.getAttributeValue("", "inboundTLS")) ? true: false;
		
		outboundTSL = "true".equals(token.getAttributeValue("", "outboundTLS")) ? true: false;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Interceptors.ELEMENT_NAME.equals(child)) {
			this.interceptors = ((Interceptors) (new Interceptors().parse(token))).getInterceptors();
		}
	}
}
