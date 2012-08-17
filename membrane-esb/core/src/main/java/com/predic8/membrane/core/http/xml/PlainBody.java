/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.Message;

public class PlainBody extends AbstractXmlElement {
	
	private final Message msg;

	public PlainBody(Message msg) {
		this.msg = msg;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeAttribute("type", "plain");
		
		try {
			out.writeCData(new String(msg.getBody().getContent(), msg.getCharset()));
		} catch (Exception e) {
			out.writeStartElement("error");
			out.writeCharacters(e.getMessage());
			out.writeEndElement();
		}
	}

}
