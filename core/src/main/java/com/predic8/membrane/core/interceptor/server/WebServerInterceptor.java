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
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.ResourceResolver;
import com.predic8.membrane.core.util.TextUtil;

public class WebServerInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WebServerInterceptor.class
			.getName());

	private static String[] EMPTY = new String[0];
	
	String docBase = "docBase";
	String[] index = EMPTY;

	public WebServerInterceptor() {
		name = "Web Server";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = URIUtil.getPath(exc.getDestinations().get(0));

		log.debug("request: " + uri);

		log.debug("looking for file: " + uri);

		try {
			exc.setTimeReqSent(System.currentTimeMillis());
			
			exc.setResponse(createResponse(router.getResourceResolver(), docBase + uri));

			exc.setReceived();
			exc.setTimeResReceived(System.currentTimeMillis());
			return Outcome.RETURN;
		} catch (FileNotFoundException e) {
			for (String i : index) {
				try {
					exc.setResponse(createResponse(router.getResourceResolver(), docBase + uri + i));

					exc.setReceived();
					exc.setTimeResReceived(System.currentTimeMillis());
					return Outcome.RETURN;
				} catch (FileNotFoundException e2) {
				}
			}
			
			exc.setResponse(HttpUtil.createNotFoundResponse());
			return Outcome.ABORT;
		}
	}

	public static Response createResponse(ResourceResolver rr, String resPath) throws IOException {
		Response response = Response.ok().header(createHeaders(getContentType(resPath))).build();
		
		InputStream in = rr.resolve(resPath, true);
		if (in == null)
			throw new FileNotFoundException(resPath);
		
		response.setBodyContent(ByteUtil.getByteArrayData(in));
		return response;
	}

	private static String getContentType(String uri) {
		if (uri.endsWith(".css"))
			return "text/css";
		if (uri.endsWith(".js"))
			return "application/x-javascript";
		if (uri.endsWith(".wsdl"))
			return "text/xml";
		if (uri.endsWith(".xml"))
			return "text/xml";
		if (uri.endsWith(".xsd"))
			return "text/xml";
		if (uri.endsWith(".html"))
			return "text/html";
		if (uri.endsWith(".jpg"))
			return "image/jpeg";
		if (uri.endsWith(".png"))
			return "image/png";
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
		if (index.length > 0)
			out.writeAttribute("index", getIndex());

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {
		docBase = token.getAttributeValue("", "docBase");
		setIndex(token.getAttributeValue("", "index"));
	}
	
	public String getIndex() {
		return StringUtils.join(index, ",");
	}
	
	public void setIndex(String i) {
		if (i == null)
			index = EMPTY;
		else
			index = i.split(",");
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
