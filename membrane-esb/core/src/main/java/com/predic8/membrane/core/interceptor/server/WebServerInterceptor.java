/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import static com.predic8.membrane.core.util.HttpUtil.createHeaders;

import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.TextUtil;

public class WebServerInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WebServerInterceptor.class
			.getName());

	String docBase = "docBase";

	public WebServerInterceptor() {
		name = "Web Server";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = URIUtil.getPath(exc.getDestinations().get(0));

		log.debug("request: " + uri);

		log.debug("looking for file: " + uri);

		try {
			exc.setResponse(createResponse(uri));
			return Outcome.RETURN;
		} catch (FileNotFoundException e) {
			exc.setResponse(HttpUtil.createNotFoundResponse());
			return Outcome.ABORT;
		}
	}

	private Response createResponse(String uri) throws Exception {
		Response response = Response.ok().header(createHeaders(getContentType(uri))).build();

		String resPath = docBase + uri;
		
		InputStream in = router.getResourceResolver().resolve(resPath, true);
		if (in == null)
			throw new FileNotFoundException(resPath);
		
		response.setBodyContent(ByteUtil.getByteArrayData(in));
		return response;
	}

	private String getContentType(String uri) {
		if (uri.endsWith(".css"))
			return "text/css";
		if (uri.endsWith(".js"))
			return "application/x-javascript";
		if (uri.endsWith(".wsdl"))
			return "text/xml";
		return null;
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

	@Override
	public String getShortDescription() {
		return "Serves static files from<br/>" + TextUtil.linkURL(docBase) + " .";
	}
	
	@Override
	public String getHelpId() {
		return "web-server";
	}

}
