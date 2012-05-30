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
package com.predic8.membrane.core.interceptor.server;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.httpclient.util.URIUtil;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.ws.relocator.Relocator.PathRewriter;

public class WSDLPublisherInterceptor extends AbstractInterceptor {

	private final class RelativePathRewriter implements PathRewriter {
		private final Exchange exc;
		private final String resource;

		private RelativePathRewriter(Exchange exc, String resource) {
			this.exc = exc;
			this.resource = resource;
		}

		@Override
		public String rewrite(String path) {
			if (path.contains("://") || path.startsWith("/"))
				return path;
			try {
				path = router.getResourceResolver().combine(resource, path);
				synchronized(paths) {
					if (paths_reverse.containsKey(path)) {
						path = paths_reverse.get(path).toString();
					} else {
						int n = paths.size() + 1;
						paths.put(n, path);
						paths_reverse.put(path, n);
						path = Integer.toString(n);
					}
				}
				path = URIUtil.getPath(exc.getDestinations().get(0)) + "?xsd=" + path;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return path;
		}
	}

	private final HashMap<Integer, String> paths = new HashMap<Integer, String>();
	private final HashMap<String, Integer> paths_reverse = new HashMap<String, Integer>();
	
	
	private String wsdl;
	
	public String getWsdl() {
		return wsdl;
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("wsdlPublisher");
		out.writeAttribute("wsdl", wsdl);
		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {
		wsdl = token.getAttributeValue("", "wsdl");
	}
	
	@Override
	public Outcome handleRequest(final Exchange exc) throws Exception {
		String resource = null;
		try {
			if (exc.getRequestURI().endsWith("?wsdl")) {
				exc.setResponse(WebServerInterceptor.createResponse(router.getResourceResolver(), resource = wsdl));
			}
			if (exc.getRequestURI().contains("?xsd=")) {
				Map<String, String> params = URLParamUtil.getParams(exc);
				if (params.containsKey("xsd")) {
					String path = params.get("xsd");
					synchronized(paths) {
						if (!paths.containsKey(Integer.parseInt(path))) {
							exc.setResponse(Response.forbidden("Please retrieve the WSDL first. You may only retrieve documents referenced by the WSDL.").build());
							return Outcome.ABORT;
						}
						path = paths.get(Integer.parseInt(path));
					}
					exc.setResponse(WebServerInterceptor.createResponse(router.getResourceResolver(), resource = path));
				}
			}
			if (resource != null) {
				WSDLInterceptor wi = new WSDLInterceptor();
				wi.setPathRewriter(new RelativePathRewriter(exc, resource));
				wi.handleResponse(exc);
				return Outcome.RETURN;
			}
		} catch (FileNotFoundException e) {
			exc.setResponse(HttpUtil.createNotFoundResponse());
			return Outcome.ABORT;
		}
			
		return Outcome.CONTINUE;
	}
	
	@Override
	public String getShortDescription() {
		return "Publishes the WSDL at " + wsdl + " under \"?wsdl\" (as well as its dependent schemas under similar URLs).";
	}
	
	@Override
	public String getHelpId() {
		return "wsdl-publisher";
	}

}
