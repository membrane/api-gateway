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
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.TextUtil;
import com.predic8.membrane.core.util.URLUtil;

@MCElement(name="webServer")
public class WebServerInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WebServerInterceptor.class
			.getName());

	private static String[] EMPTY = new String[0];
	
	String docBase = "docBase";
	String[] index = EMPTY;
	boolean generateIndex;

	public WebServerInterceptor() {
		name = "Web Server";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = URLUtil.getPathFromPathQuery(URLUtil.getPathQuery(exc.getDestinations().get(0)));

		log.debug("request: " + uri);

		log.debug("looking for file: " + uri);
		
		if (uri.endsWith("..") || uri.endsWith("../") || uri.endsWith("..\\")) {
			exc.setResponse(Response.badRequest().body("").build());
			return Outcome.ABORT;
		}

		try {
			exc.setTimeReqSent(System.currentTimeMillis());
			
			exc.setResponse(createResponse(router.getResolverMap(), docBase + uri));

			exc.setReceived();
			exc.setTimeResReceived(System.currentTimeMillis());
			return Outcome.RETURN;
		} catch (FileNotFoundException e) {
			for (String i : index) {
				try {
					exc.setResponse(createResponse(router.getResolverMap(), docBase + uri + i));

					exc.setReceived();
					exc.setTimeResReceived(System.currentTimeMillis());
					return Outcome.RETURN;
				} catch (FileNotFoundException e2) {
				}
			}
			
			if (generateIndex) {
				List<String> children = router.getResolverMap().getChildren(docBase + uri);
				if (children != null) {
					Collections.sort(children);
					StringBuilder sb = new StringBuilder();
					sb.append("<html><body><tt>");
					String base = uri;
					if (base.endsWith("/"))
						base = "";
					else {
						base = exc.getRequestURI();
						int p = base.lastIndexOf('/');
						if (p != -1)
							base = base.substring(p+1);
						base = base + "/";
					}
					for (String child : children)
						sb.append("<a href=\"" + base + child + "\">" + child + "</a><br/>");
					sb.append("</tt></body></html>");
					exc.setResponse(Response.ok().contentType("text/html").body(sb.toString()).build());
					return Outcome.RETURN;
				}
			}
			
			exc.setResponse(Response.notFound().build());
			return Outcome.ABORT;
		}
	}

	public static Response createResponse(ResolverMap rr, String resPath) throws IOException {
		Response response = Response.ok().header(createHeaders(getContentType(resPath))).build();
		
		InputStream in = rr.resolve(resPath);
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

	@Required
	@MCAttribute
	public void setDocBase(String docBase) {
		this.docBase = docBase;
	}
	
	public String getIndex() {
		return StringUtils.join(index, ",");
	}
	
	@MCAttribute
	public void setIndex(String i) {
		if (i == null)
			index = EMPTY;
		else
			index = i.split(",");
	}
	
	public boolean isGenerateIndex() {
		return generateIndex;
	}
	
	@MCAttribute
	public void setGenerateIndex(boolean generateIndex) {
		this.generateIndex = generateIndex;
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
