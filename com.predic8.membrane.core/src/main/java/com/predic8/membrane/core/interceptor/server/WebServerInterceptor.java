/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.server;

import java.io.*;
import java.util.Date;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;

public class WebServerInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WebServerInterceptor.class
			.getName());

	String docBase = "docBase";

	public WebServerInterceptor() {
		name = "Web Server";
		setFlow(Flow.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getOriginalRequestUri();

		log.debug("request: " + uri);

		log.debug("looking for file: " + uri);

		try {
			exc.setResponse(createResponse(uri));
		} catch (FileNotFoundException e) {
			exc.setResponse(HttpUtil.createNotFoundResponse());
		}

		return Outcome.ABORT;
	}

	private Response createResponse(String uri) throws Exception {
		Response response = Response.ok().build();
		response.setHeader(createHeader(uri));

		String resPath = new File(docBase, uri).getPath().replace('\\', '/'); // TODO replace() is a temporary fix
		
		InputStream in = router.getResourceResolver().resolve(resPath, true);
		
		if (in == null) {
			throw new FileNotFoundException(resPath);
		}
		
		response.setBodyContent(ByteUtil.getByteArrayData(in));
		return response;
	}

	private void setContentType(Header h, String uri) {
		if (uri.endsWith(".css")) {
			h.setContentType("text/css");
		} else if (uri.endsWith(".js")) {
			h.setContentType("application/x-javascript");
		}
	}

	private Header createHeader(String uri) {
		Header header = new Header();
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		setContentType(header, uri);
		return header;
	}

	public String getDocBase() {
		return docBase;
	}

	public void setDocBase(String docBase) {
		this.docBase = docBase;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("webServer");

		out.writeAttribute("docBase", docBase);

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {

		docBase = token.getAttributeValue("", "docBase");
	}

}
