package com.predic8.plugin.membrane.util;

import java.io.*;

import javax.xml.stream.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.TextUtil;

public class RuleUtil {

	
	public Rule createCopy(Rule original) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(baos, Constants.UTF_8);
		original.write(writer);
		ByteArrayInputStream stream = new ByteArrayInputStream(baos.toByteArray());
		InputStreamReader reader = new InputStreamReader(stream, Constants.UTF_8);
		String xml = TextUtil.formatXML(reader);
		return null;
		
	}
	
	public static XMLStreamReader getStreamReaderFor(byte[] bytes) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
	    return factory.createXMLStreamReader(stream);
	}
	
}
