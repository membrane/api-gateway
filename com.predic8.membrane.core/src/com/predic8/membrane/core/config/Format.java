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

public class Format extends AbstractConfigElement {

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
