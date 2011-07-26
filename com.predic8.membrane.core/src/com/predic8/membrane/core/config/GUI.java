/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.Router;

public class GUI extends AbstractConfigElement {

	public GUI(Router router) {
		super(router);
	}


	public static final String ELEMENT_NAME = "gui";
	
	public Map<String, Object> values = new HashMap<String, Object>();
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (AutoTrackExchange.ELEMENT_NAME.equals(child)) {
			boolean value = ((AutoTrackExchange) new AutoTrackExchange(router).parse(token)).getValue();
			values.put(Configuration.TRACK_EXCHANGE, value);
		} else if (IndentMessage.ELEMENT_NAME.equals(child)) {
			boolean value = ((IndentMessage)(new IndentMessage(router).parse(token))).getValue();
			values.put(Configuration.INDENT_MSG, value);
		} 
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> newValues) {
		if (newValues.containsKey(Configuration.TRACK_EXCHANGE)) {
			this.values.put(Configuration.TRACK_EXCHANGE, newValues.get(Configuration.TRACK_EXCHANGE));
		} 
		
		if (newValues.containsKey(Configuration.INDENT_MSG)) {
			this.values.put(Configuration.INDENT_MSG, newValues.get(Configuration.INDENT_MSG));
		} 
	}
	
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		AutoTrackExchange trackExchange = new AutoTrackExchange(router);
		if (values.containsKey(Configuration.TRACK_EXCHANGE)) {
			trackExchange.setValue((Boolean)values.get(Configuration.TRACK_EXCHANGE));
		}
		trackExchange.write(out);
		
		IndentMessage indentMessage = new IndentMessage(router);
		if (values.containsKey(Configuration.INDENT_MSG)) {
			indentMessage.setValue((Boolean)values.get(Configuration.INDENT_MSG));
		}
		indentMessage.write(out);
		
		out.writeEndElement();
	}
	
}
