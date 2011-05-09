package com.predic8.membrane.core.http.xml;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.HeaderField;

public class Headers extends AbstractXmlElement {
	public static final String ELEMENT_NAME = "headers";

	List<Header> headers = new ArrayList<Header>();
	
	public Headers() {}
	
	public Headers( com.predic8.membrane.core.http.Header header) {
		for (Object o : header.getAllHeaderFields()) {
			HeaderField h = (HeaderField)o;
			headers.add(new Header(""+h.getHeaderName(), h.getValue()));
		}
	}
		
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Param.ELEMENT_NAME.equals(child)) {
			headers.add((Header)new Header().parse(token));
		} 
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		for (Header c : headers) {
			c.write(out);
		}
		
		out.writeEndElement();		
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
}
