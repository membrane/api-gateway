package com.predic8.membrane.core.http.xml;

import java.net.URISyntaxException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;

public class URI extends AbstractXmlElement {
	
	public static final String ELEMENT_NAME = "uri";

	String value;
	Port port;
	Host host;
	Path path;
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		value = token.getAttributeValue("", "value");
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Port.ELEMENT_NAME.equals(child)) {
			port = (Port) new Port().parse(token);
		} else if (Host.ELEMENT_NAME.equals(child)) {
			host = (Host) new Host().parse(token);
		} else if (Path.ELEMENT_NAME.equals(child)) {
			path = (Path) new Path().parse(token);
		}
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		out.writeAttribute("value", value);
		writeIfNotNull(host, out);
		writeIfNotNull(port, out);
		writeIfNotNull(path, out);
		out.writeEndElement();		
	}

	private void writeIfNotNull(AbstractXmlElement e, XMLStreamWriter out) throws XMLStreamException {
		if (e != null ) e.write(out);
	}
	
	public int getPort() {
		return port.getValue();
	}

	public void setPort(int port) {
		this.port = new Port(port);
	}

	public String getHost() {
		return host.getValue();
	}

	public void setHost(String host) {
		this.host = new Host(host);
	}

	public Path getPath() {
		return path;
	}

	public void setPath(Path path) {
		this.path = path;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) throws URISyntaxException {
		this.value = value;

		java.net.URI jUri = new java.net.URI(value); 
		
		if (jUri.getHost()!=null)
			setHost(jUri.getHost());
		
		if (jUri.getPort()!=-1)
			setPort(jUri.getPort());
		
		if (jUri.getPath() == null) return;
		Path p = new Path();
		for (String c : jUri.getPath().substring(1).split("/")) {
			p.getComponents().add(new Component(c));
		}
		setPath(p);
	}

}
