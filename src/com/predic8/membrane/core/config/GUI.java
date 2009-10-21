package com.predic8.membrane.core.config;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;

public class GUI extends XMLElement {

	public static final String ELEMENT_NAME = "gui";
	
	public Map<String, Object> values = new HashMap<String, Object>();
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (AutoTrackExchange.ELEMENT_NAME.equals(child)) {
			boolean value = ((AutoTrackExchange)new AutoTrackExchange().parse(token)).getValue();
			values.put(Configuration.TRACK_EXCHANGE, value);
		}
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> values) {
		if (values.containsKey(Configuration.TRACK_EXCHANGE)) {
			this.values.put(Configuration.TRACK_EXCHANGE, values.get(Configuration.TRACK_EXCHANGE));
		}
	}
	
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		AutoTrackExchange trackExchange = new AutoTrackExchange();
		if (values.containsKey(Configuration.TRACK_EXCHANGE)) {
			trackExchange.setValue((Boolean)values.get(Configuration.TRACK_EXCHANGE));
		}
		trackExchange.write(out);
		out.writeEndElement();
	}
	
}
