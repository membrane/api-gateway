package com.predic8.plugin.membrane.util;

import java.io.ByteArrayInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class RuleUtil {

	public static XMLStreamReader getStreamReaderFor(byte[] bytes) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
	    return factory.createXMLStreamReader(stream);
	}
	
}
