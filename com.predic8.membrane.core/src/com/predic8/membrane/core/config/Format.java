package com.predic8.membrane.core.config;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;

public class Format extends XMLElement {

	public static final String ELEMENT_NAME = "format";

	public Map<String, Object> values = new HashMap<String, Object>();

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (IndentMessage.ELEMENT_NAME.equals(child)) {
			boolean value = ((IndentMessage)(new IndentMessage().parse(token))).getValue();
			values.put(Configuration.INDENT_MSG, value);
		} else if (AdjustHostHeader.ELEMENT_NAME.equals(child)) {
			boolean value = ((AdjustHostHeader)(new AdjustHostHeader().parse(token))).getValue();
			values.put(Configuration.ADJ_HOST_HEADER, value);
		}
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> newValues) {
		if (newValues.containsKey(Configuration.ADJ_CONT_LENGTH)) {
			values.put(Configuration.ADJ_CONT_LENGTH, newValues.get(Configuration.ADJ_CONT_LENGTH));
		} 
		
		if (newValues.containsKey(Configuration.INDENT_MSG)) {
			values.put(Configuration.INDENT_MSG, newValues.get(Configuration.INDENT_MSG));
		}  
		
		if (newValues.containsKey(Configuration.ADJ_HOST_HEADER)) {
			values.put(Configuration.ADJ_HOST_HEADER, newValues.get(Configuration.ADJ_HOST_HEADER));
		} 
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		
		IndentMessage indentMessage = new IndentMessage();
		if (values.containsKey(Configuration.INDENT_MSG)) {
			indentMessage.setValue((Boolean)values.get(Configuration.INDENT_MSG));
		}
		indentMessage.write(out);
		
		AdjustHostHeader adjustHostHeader = new AdjustHostHeader();
		if (values.containsKey(Configuration.ADJ_HOST_HEADER)) {
			adjustHostHeader.setValue((Boolean)values.get(Configuration.ADJ_HOST_HEADER));
		}
		adjustHostHeader.write(out);
		
		out.writeEndElement();
	}
	
}
