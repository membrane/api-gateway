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

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.config.AbstractXmlElement;

public class Exchange extends AbstractXmlElement {

	public static final String ELEMENT_NAME = "exchange";

	private Request request;
	private Response response;

	public Exchange(com.predic8.membrane.core.exchange.Exchange exc) throws Exception {
		request = new Request(exc.getRequest());
		com.predic8.membrane.core.http.Response res = exc.getResponse();
		response = res == null ? null : new Response(res);
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (Request.ELEMENT_NAME.equals(child)) {
			request = (Request) new Request().parse(token);
		} else if (Response.ELEMENT_NAME.equals(child)) {
			response = (Response) new Headers().parse(token);
		}

	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("http", ELEMENT_NAME, Constants.HTTP_NS);

		out.writeNamespace("http", Constants.HTTP_NS);

		request.write(out);
		writeIfNotNull(response, out);

		out.writeEndElement();
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	public Request getRequest() {
		return request;
	}

	public void setRequest(Request request) {
		this.request = request;
	}

	public Response getResponse() {
		return response;
	}

	public void setResponse(Response response) {
		this.response = response;
	}
}
