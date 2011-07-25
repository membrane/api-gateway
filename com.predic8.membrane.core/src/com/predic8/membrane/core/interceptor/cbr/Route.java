package com.predic8.membrane.core.interceptor.cbr;

import javax.xml.stream.*;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class Route extends AbstractXmlElement {

	private String url;
	private String xPath;
	
	public Route() {}
	
	public Route(String xPath, String url) {
		this.url = url;
		this.xPath = xPath;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getxPath() {
		return xPath;
	}

	public void setxPath(String xPath) {
		this.xPath = xPath;
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("route");
		
		out.writeAttribute("xPath", xPath);		
		out.writeAttribute("url", url);		

		out.writeEndElement();
	}
		
	@Override
	protected void parseAttributes(XMLStreamReader token)
			throws XMLStreamException {
		xPath = token.getAttributeValue("", "xPath");
		url = token.getAttributeValue("", "url");
	}
	
	@Override
	protected String getElementName() {
		return "route";
	}
	
	

}
