package com.predic8.membrane.core.config;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

public interface XMLElement {

	public abstract XMLElement parse(XMLStreamReader token) throws XMLStreamException;

	public abstract void write(XMLStreamWriter out) throws XMLStreamException;

}