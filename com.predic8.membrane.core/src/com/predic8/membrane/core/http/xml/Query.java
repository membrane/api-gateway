package com.predic8.membrane.core.http.xml;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Query extends AbstractXmlElement {
	public static final String ELEMENT_NAME = "query";

	private List<Param> params = new ArrayList<Param>();
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Param.ELEMENT_NAME.equals(child)) {
			params.add((Param)new Param().parse(token));
		} 
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		for (Param c : params) {
			c.write(out);
		}
		
		out.writeEndElement();		
	}

	public List<Param> getParams() {
		return params;
	}

	public void setParams(List<Param> params) {
		this.params = params;
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	
}
