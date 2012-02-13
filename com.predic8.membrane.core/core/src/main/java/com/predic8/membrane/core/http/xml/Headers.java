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
package com.predic8.membrane.core.http.xml;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.HeaderField;

public class Headers extends AbstractXmlElement {
	public static final String ELEMENT_NAME = "headers";

	List<Header> headers = new ArrayList<Header>();
	
	public Headers() {}
	
	public Headers( com.predic8.membrane.core.http.Header header) {
		for (HeaderField o : header.getAllHeaderFields()) {
			headers.add(new Header("" + o.getHeaderName(), o.getValue()));
		}
	}
		
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (Param.ELEMENT_NAME.equals(child)) {
			headers.add((Header)new Header().parse(token));
		} 
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);

		for (Header c : headers) {
			c.write(out);
		}
		
		out.writeEndElement();		
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
}
