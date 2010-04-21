package com.predic8.membrane.core.interceptor;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXMLElement;
import com.predic8.membrane.core.exchange.Exchange;

public class AbstractInterceptor extends AbstractXMLElement implements Interceptor {

	public static final String ELEMENT_NAME = "interceptor";
	
	protected String name = this.getClass().getName();
	
	protected String id;
	
	protected Router router;
	
	public Outcome handleRequest(Exchange exc) throws Exception {
		return Outcome.CONTINUE;
	}

	public Outcome handleResponse(Exchange exc) throws Exception {
		return Outcome.CONTINUE;
	}

	public String getDisplayName() {
		return name;
	}

	public void setDisplayName(String name) {
		this.name = name;
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		name = token.getAttributeValue("", "name");	
		id = token.getAttributeValue("", "id");	
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		out.writeAttribute("id", getId());
		
		out.writeAttribute("name", getDisplayName());
		
		out.writeEndElement();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setRouter(Router router) {
		this.router = router;
	}

	
}
