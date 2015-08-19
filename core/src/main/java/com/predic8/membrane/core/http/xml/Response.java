/* Copyright 2013 predic8 GmbH, www.predic8.com

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
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.core.Constants;

public class Response extends Message {

	public static final String ELEMENT_NAME = "response";

	private int statusCode;
	private String statusMessage;

	public Response(com.predic8.membrane.core.http.Response res) throws Exception {
		super(res);

		setStatusCode(res.getStatusCode());
		setStatusMessage(res.getStatusMessage());
	}

	public Response() {
		super();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (Headers.ELEMENT_NAME.equals(child)) {
			headers = (Headers) new Headers().parse(token);
		} else if ("status".equals(child)) {
			statusCode = Integer.parseInt(StringUtils.defaultIfBlank(token.getAttributeValue("", "status-code"), "0"));
			statusMessage = "";
			while (token.hasNext()) {
				token.next();
				if (token.isStartElement()) {
					parseChildren(token, token.getName().getLocalPart());
				} else if (token.isCharacters()) {
					statusMessage += token.getText();
				} else if (token.isEndElement()) {
					break;
				}
			}
		}
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("http", ELEMENT_NAME, Constants.HTTP_NS);

		out.writeNamespace("http", Constants.HTTP_NS);

		out.writeStartElement("status");
		out.writeAttribute("code", "" + statusCode);
		out.writeCharacters(statusMessage);
		out.writeEndElement();

		writeIfNotNull(headers, out);
		if (body != null) {
			out.writeStartElement("body");
			body.write(out);
			out.writeEndElement();
		}

		out.writeEndElement();
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

}
