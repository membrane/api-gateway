package com.predic8.membrane.core.interceptor.balancer;

import java.util.*;

import javax.xml.stream.*;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor.Mapping;

public class EndpointsElement extends AbstractXmlElement {

	private List<Node> endpoints = new ArrayList<Node>();
		
	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("endpoints");

		for (Node n : endpoints) {
			n.write(out);
		}

		out.writeEndElement();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws XMLStreamException {
		if ("node".equals(child)) {
			Node n = new Node();
			n.parse(token);
			endpoints.add(n);
		} else {
			super.parseChildren(token, child);
		}
	}
	
	@Override
	protected String getElementName() {
		return "endpoints";
	}

	public List<Node> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<Node> endpoints) {
		this.endpoints = endpoints;
	}
	
}
