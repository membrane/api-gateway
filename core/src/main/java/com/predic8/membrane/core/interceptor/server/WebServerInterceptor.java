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
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.TextUtil;

/**
 * @description Serves static files based on the request's path.
 * @explanation <p>
 *              Note that <i>docBase</i> any <i>location</i>: A relative or absolute directory, a
 *              "classpath://com.predic8.membrane.core.interceptor.administration.docBase" expression or a URL.
 *              </p>
 *              <p>
 *              The interceptor chain will not continue beyond this interceptor, as it either successfully returns a
 *              HTTP response with the contents of a file, or a "404 Not Found." error.
 *              </p>
 * @topic 4. Interceptors/Features
 */
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
		String uri = router.getUriFactory().create(exc.getDestinations().get(0)).getPath();

		log.debug("request: " + uri);

		if (uri.endsWith("..") || uri.endsWith("../") || uri.endsWith("..\\") || uri.contains("/../") || uri.startsWith("..")) {
			exc.setResponse(Response.badRequest().body("").build());
			return Outcome.ABORT;
		}

		if (uri.startsWith("/"))
			uri = uri.substring(1);


		try {
			exc.setTimeReqSent(System.currentTimeMillis());

			exc.setResponse(createResponse(router.getResolverMap(), ResolverMap.combine(router.getBaseLocation(), docBase,  uri)));

			exc.setReceived();
			exc.setTimeResReceived(System.currentTimeMillis());
			return Outcome.RETURN;
		} catch (ResourceRetrievalException e) {
			String uri2 = uri;
			if (!uri2.endsWith("/")) {
				uri2 += "/";
				for (String i : index) {
					try {
						router.getResolverMap().resolve(ResolverMap.combine(router.getBaseLocation(), docBase, uri2 + i));
						// no exception? then the URI was a directory without trailing slash
						exc.setResponse(Response.redirect(exc.getRequest().getUri() + "/", false).build());
						return Outcome.RETURN;
					} catch (Exception e2) {
						continue;
					}
				}
			} else {
				for (String i : index) {
					try {
						exc.setResponse(createResponse(router.getResolverMap(), ResolverMap.combine(router.getBaseLocation(), docBase, uri2 + i)));

						exc.setReceived();
						exc.setTimeResReceived(System.currentTimeMillis());
						return Outcome.RETURN;
					} catch (FileNotFoundException e2) {
					}
				}
			}

			if (generateIndex) {
				List<String> children = router.getResolverMap().getChildren(ResolverMap.combine(router.getBaseLocation(), docBase, uri));
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
						if (base.length() == 0)
							base = ".";
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
		return Response.ok()
				.header(createHeaders(getContentType(resPath)))
				.body(rr.resolve(resPath), true)
				.build();
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
		if (uri.endsWith(".json"))
			return "application/json";
		return null;
	}

	public String getDocBase() {
		return docBase;
	}

	/**
	 * @description Sets path to the directory that contains the web content.
	 * @default docBase
	 * @example docBase
	 */
	@Required
	@MCAttribute
	public void setDocBase(String docBase) {
		if (!docBase.endsWith("/"))
			docBase += "/";
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

}
