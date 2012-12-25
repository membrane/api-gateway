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
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import javax.annotation.concurrent.GuardedBy;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.SOAPProxy;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.URLParamUtil;
import com.predic8.membrane.core.util.URLUtil;
import com.predic8.membrane.core.ws.relocator.Relocator.PathRewriter;

@MCInterceptor(xsd="" +
		"	<xsd:element name=\"wsdlPublisher\">\r\n" + 
		"		<xsd:complexType>\r\n" + 
		"			<xsd:complexContent>\r\n" + 
		"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
		"					<xsd:sequence />\r\n" + 
		"					<xsd:attribute name=\"wsdl\" type=\"xsd:string\" />\r\n" + 
		"				</xsd:extension>\r\n" + 
		"			</xsd:complexContent>\r\n" + 
		"		</xsd:complexType>\r\n" + 
		"	</xsd:element>\r\n" + 
		"")
public class WSDLPublisherInterceptor extends AbstractInterceptor {

	private static Logger log = LogManager.getLogger(WSDLPublisherInterceptor.class);
	
	public WSDLPublisherInterceptor() {
		name = "WSDL Publisher";
	}
	
	/**
	 * Note that this class fulfills two purposes:
	 * 
	 * * During the initial processDocuments() run, the XSDs are enumerated.
	 * 
	 * * During later runs (as well as the initial run, but that's result is discarded),
	 * the documents are rewritten.
	 */
	private final class RelativePathRewriter implements PathRewriter {
		private final Exchange exc;
		private final String resource;

		private RelativePathRewriter(Exchange exc, String resource) {
			this.exc = exc;
			this.resource = resource;
		}

		@Override
		public String rewrite(String path) {
			try {
				if (!path.contains("://") && !path.startsWith("/")) // relative?
					path = router.getResourceResolver().combine(resource, path);
				synchronized(paths) {
					if (paths_reverse.containsKey(path)) {
						path = paths_reverse.get(path).toString();
					} else {
						int n = paths.size() + 1;
						paths.put(n, path);
						paths_reverse.put(path, n);
						documents_to_process.add(path);
						path = Integer.toString(n);
					}
				}
				path = "./" + URLUtil.getName(exc.getDestinations().get(0)) + "?xsd=" + path;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return path;
		}
	}

	@GuardedBy("paths")
	private final HashMap<Integer, String> paths = new HashMap<Integer, String>();
	@GuardedBy("paths")
	private final HashMap<String, Integer> paths_reverse = new HashMap<String, Integer>();
	@GuardedBy("paths")
	private final Queue<String> documents_to_process = new LinkedList<String>();
	
	private void processDocuments(Exchange exc) throws Exception {
		// exc.response is only temporarily used so we can call the WSDLInterceptor
		// exc.response is set to garbage and should be discarded after this method
		synchronized (paths) {
			try {
				while (true) {
					String doc = documents_to_process.poll();
					if (doc == null)
						break;
					log.debug("WSDLPublisherInterceptor: processing " + doc);

					exc.setResponse(WebServerInterceptor.createResponse(router.getResourceResolver(), doc));
					WSDLInterceptor wi = new WSDLInterceptor();
					wi.setRewriteEndpoint(false);
					wi.setPathRewriter(new RelativePathRewriter(exc, doc));
					wi.handleResponse(exc);
				}
			} catch (FileNotFoundException e) {
				log.error("Could not recursively load all documents referenced by '"+wsdl+"'.", e);
			}
		}
	}
	
	private String wsdl;
	
	public String getWsdl() {
		return wsdl;
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
		synchronized(paths) {
			paths.clear();
			paths_reverse.clear();
			documents_to_process.clear();
			documents_to_process.add(wsdl);
		}
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
		setWsdl(token.getAttributeValue("", "wsdl"));
	}
	
	@Override
	public void init() throws Exception {
		// inherit wsdl="..." from SoapProxy
		if (wsdl == null) {
			Rule parent = router.getParentProxy(this);
			if (parent instanceof SOAPProxy) {
				wsdl = ((SOAPProxy)parent).getWsdl();
				setWsdl(wsdl);
			}
		}
	}
	
	@Override
	public Outcome handleRequest(final Exchange exc) throws Exception {
		if (!"GET".equals(exc.getRequest().getMethod()))
			return Outcome.CONTINUE;
		
		try {
			String resource = null;
			if (exc.getRequestURI().endsWith("?wsdl") || exc.getRequestURI().endsWith("?WSDL")) {
				processDocuments(exc);
				exc.setResponse(WebServerInterceptor.createResponse(router.getResourceResolver(), resource = wsdl));
				exc.getResponse().getHeader().setContentType(MimeType.TEXT_XML);
			}
			if (exc.getRequestURI().contains("?xsd=")) {
				Map<String, String> params = URLParamUtil.getParams(exc);
				if (params.containsKey("xsd")) {
					int n = Integer.parseInt(params.get("xsd"));
					String path;
					processDocuments(exc);
					synchronized(paths) {
						if (!paths.containsKey(n)) {
							exc.setResponse(Response.forbidden("Unknown parameter. You may only retrieve documents referenced by the WSDL.").build());
							return Outcome.ABORT;
						}
						path = paths.get(n);
					}
					exc.setResponse(WebServerInterceptor.createResponse(router.getResourceResolver(), resource = path));
					exc.getResponse().getHeader().setContentType(MimeType.TEXT_XML);
				}
			}
			if (resource != null) {
				WSDLInterceptor wi = new WSDLInterceptor();
				wi.setRewriteEndpoint(false);
				wi.setPathRewriter(new RelativePathRewriter(exc, resource));
				wi.handleResponse(exc);
				return Outcome.RETURN;
			}
		} catch (NumberFormatException e) {
			exc.setResponse(HttpUtil.createHTMLErrorResponse("Bad parameter format.", ""));
			return Outcome.ABORT;
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
