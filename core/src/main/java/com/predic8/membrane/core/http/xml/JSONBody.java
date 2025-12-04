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

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.json.JsonFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.ArrayList;
import java.util.List;

import static tools.jackson.core.JsonToken.VALUE_NUMBER_INT;
import static tools.jackson.core.JsonToken.VALUE_STRING;

class JSONBody extends AbstractXmlElement {

	private final Message msg;

	public JSONBody(Message msg) {
		this.msg = msg;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeAttribute("type", "json");
		try {
			JsonParser jp = new JsonFactory().createParser(msg.getBodyAsStreamDecoded());
			List<String> stack = new ArrayList<>();
			String name = "root";

			OUTER:
			while (jp.nextToken() != null) {
				switch (jp.currentToken()) {
					case START_OBJECT:
						if (name != null) {
							stack.add(name);
							out.writeStartElement(name);
							out.writeAttribute("type", "o");
							name = null;
						}
						break;

					case END_OBJECT:
						out.writeEndElement();
						name = stack.remove(stack.size() - 1);
						if (stack.isEmpty())
							break OUTER;
						break;

					case PROPERTY_NAME: // statt FIELD_NAME
						name = jp.currentName();
						break;

					case START_ARRAY:
						if (name != null) {
							stack.add(name);
							out.writeStartElement(name);
							out.writeAttribute("type", "a");
						}
						name = "item";
						break;

					case END_ARRAY:
						out.writeEndElement();
						name = stack.remove(stack.size() - 1);
						if (stack.isEmpty())
							break OUTER;
						break;

					case VALUE_TRUE:
					case VALUE_FALSE:
						out.writeStartElement(name);
						out.writeAttribute("type", "b");
						out.writeCharacters(jp.getString()); // "true"/"false"
						out.writeEndElement();
						break;

					case VALUE_NULL:
						out.writeStartElement(name);
						out.writeAttribute("type", "n");
						out.writeAttribute("isNull", "true");
						out.writeEndElement();
						break;

					case VALUE_STRING:
					case VALUE_NUMBER_INT:
					case VALUE_NUMBER_FLOAT:
						out.writeStartElement(name);
						JsonToken t = jp.currentToken();
						out.writeAttribute("type",
								t == VALUE_STRING    ? "s" :
										t == VALUE_NUMBER_INT ? "i" : "f");
						out.writeCharacters(jp.getString());
						out.writeEndElement();
						break;

					case VALUE_EMBEDDED_OBJECT:
					case NOT_AVAILABLE:
						throw new RuntimeException(jp.currentToken().toString());
				}
			}
		} catch (JacksonException e) {
			throw new RuntimeException(e);
		}
	}


}
