/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

public class Request extends Message {

	public static final String ELEMENT_NAME = "request";

	private String method;	
	private String httpVersion;

	private URI uri;
	
	public Request(com.predic8.membrane.core.http.Request req) throws Exception {
		super(req);
		
		setMethod(req.getMethod());
		setHttpVersion(req.getVersion());

		setUri(new URI(req.getUri()));
	}

	public Request() {
		super();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		method = token.getAttributeValue("", "method");
		httpVersion = token.getAttributeValue("", "http-version");
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (URI.ELEMENT_NAME.equals(child)) {
			uri = (URI) new URI().parse(token);
		} else if (Headers.ELEMENT_NAME.equals(child)) {
			headers = (Headers) new Headers().parse(token);
		} 
 
	}
	 
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("http", ELEMENT_NAME, Constants.HTTP_NS);
		
		out.writeNamespace("http", Constants.HTTP_NS);

		out.writeAttribute("method", method);
		out.writeAttribute("http-version", httpVersion);
		
		uri.write(out);
		writeIfNotNull(headers, out);
		if (body != null) {
			out.writeStartElement("body");
			body.write(out);
			out.writeEndElement();
		}
		
		out.writeEndElement();		
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getHttpVersion() {
		return httpVersion;
	}

	public void setHttpVersion(String httpVersion) {
		this.httpVersion = httpVersion;
	}

	public URI getUri() {
		return uri;
	}

	public void setUri(URI uri) {
		this.uri = uri;
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
}
