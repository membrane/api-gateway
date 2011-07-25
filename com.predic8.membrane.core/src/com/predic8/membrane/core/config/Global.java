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

public class Global extends AbstractConfigElement {

	public Global(Router router) {
		super(router);
	}

	public static final String ELEMENT_NAME = "global";

	public Map<String, Object> values = new HashMap<String, Object>();

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (AdjustHostHeader.ELEMENT_NAME.equals(child)) {
			boolean value = ((AdjustHostHeader)(new AdjustHostHeader(router).parse(token))).getValue();
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
		
		if (newValues.containsKey(Configuration.ADJ_HOST_HEADER)) {
			values.put(Configuration.ADJ_HOST_HEADER, newValues.get(Configuration.ADJ_HOST_HEADER));
		} 
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		
		AdjustHostHeader adjustHostHeader = new AdjustHostHeader(router);
		if (values.containsKey(Configuration.ADJ_HOST_HEADER)) {
			adjustHostHeader.setValue((Boolean)values.get(Configuration.ADJ_HOST_HEADER));
		}
		adjustHostHeader.write(out);
		
		out.writeEndElement();
	}
	
}
