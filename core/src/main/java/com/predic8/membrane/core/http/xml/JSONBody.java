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

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.http.Message;

class JSONBody extends AbstractXmlElement {

	private final Message msg;

	public JSONBody(Message msg) {
		this.msg = msg;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeAttribute("type", "json");

		try {

			final JsonFactory jsonFactory = new JsonFactory();
			final JsonParser jp = jsonFactory.createParser(new InputStreamReader(msg.getBodyAsStreamDecoded(), msg.getCharset()));
			final List<String> stack = new ArrayList<String>();
			String name = "root";
			OUTER:
				while (jp.nextToken() != null) {
					switch (jp.getCurrentToken()) {
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
						name = stack.remove(stack.size()-1);
						if (stack.isEmpty())
							break OUTER;
						break;
					case FIELD_NAME:
						name = jp.getCurrentName();
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
						name = stack.remove(stack.size()-1);
						if (stack.isEmpty())
							break OUTER;
						break;
					case VALUE_TRUE:
					case VALUE_FALSE:
						out.writeStartElement(name);
						out.writeAttribute("type", "b");
						out.writeCharacters(Boolean.toString(jp.getBooleanValue()));
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
						out.writeAttribute("type",
								jp.getCurrentToken() == JsonToken.VALUE_STRING ? "s" :
									jp.getCurrentToken() == JsonToken.VALUE_NUMBER_INT ? "i" :
								"f");
						out.writeCharacters(jp.getText());
						out.writeEndElement();
						break;
					case VALUE_EMBEDDED_OBJECT:
					case NOT_AVAILABLE:
						throw new RuntimeException(jp.getCurrentToken().toString());
					}
				}

		} catch (JsonParseException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
