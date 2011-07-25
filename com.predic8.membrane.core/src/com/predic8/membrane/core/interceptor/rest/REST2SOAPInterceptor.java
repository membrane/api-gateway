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
package com.predic8.membrane.core.interceptor.rest;

import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.config.GenericConfigElement;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor.Mapping;
import com.predic8.membrane.core.interceptor.xslt.XSLTTransformer;

public class REST2SOAPInterceptor extends AbstractInterceptor {

	public static class Mapping {
		public String regex;
		public String soapAction;
		public String soapURI;
		public String requestXSLT;
		public String responseXSLT;
		
		public Mapping(String regex, String soapAction, String soapURI, String requestXSLT, String responseXSLT) {
			this.regex = regex;
			this.soapAction = soapAction;
			this.soapURI = soapURI;
			this.requestXSLT = requestXSLT;
			this.responseXSLT = responseXSLT;
		}
	}
	
	private static Log log = LogFactory.getLog(REST2SOAPInterceptor.class.getName());

	private List<Mapping> mappings = new ArrayList<Mapping>();	
	private XSLTTransformer xsltTransformer = new XSLTTransformer();

	public REST2SOAPInterceptor() {
		name = "REST 2 SOAP Gateway";
		priority = 140;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("uri: " + getURI(exc));
		String uri = getURI(exc);

		Mapping mapping = findFirstMatchingRegEx(uri);
		if (mapping == null)
			return Outcome.CONTINUE;

		transformAndReplaceBody(exc.getRequest(), 
								mapping.requestXSLT, 
								getRequestXMLSource(exc));
		modifyRequest(exc, mapping);

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("restURL: " + getRESTURL(exc));
		if (getRESTURL(exc)==null) return Outcome.CONTINUE;

		log.debug("response: " + xsltTransformer.transform(null, getBodySource(exc)));		
		
		transformAndReplaceBody(exc.getResponse(),
				                getRESTURL(exc).responseXSLT,
				                getBodySource(exc));
		setContentType(exc.getResponse().getHeader());
		return Outcome.CONTINUE;
	}

	private StreamSource getRequestXMLSource(Exchange exc) throws Exception {
		Request req = new Request(exc.getRequest());
		
		String res = req.toXml();
		log.debug("http-xml: " + res);		
		
		return new StreamSource(new StringReader(res));
	}

	private StreamSource getBodySource(Exchange exc) {
		return new StreamSource(exc.getResponse().getBodyAsStream());
	}

	private Mapping getRESTURL(Exchange exc) {
		return (Mapping) exc.getProperty("mapping");
	}

	private Mapping findFirstMatchingRegEx(String uri) {
		for (Mapping m : mappings) {
			if (Pattern.matches(m.regex, uri))
				return m;
		}
		return null;
	}

	private void modifyRequest(AbstractExchange exc, Mapping mapping) {

		exc.getRequest().setMethod("POST");
		exc.getRequest().getHeader().setSOAPAction(mapping.soapAction);
		setContentType(exc.getRequest().getHeader());
		
		exc.setProperty("mapping", mapping);
		setServiceEndpoint(exc, mapping);
	}

	private void setContentType(Header header) {
		header.removeFields(Header.CONTENT_TYPE);
		header.setContentType("text/xml;charset=UTF-8");
	}

	private void setServiceEndpoint(AbstractExchange exc, Mapping mapping) {
		exc.getRequest().setUri(
				getURI(exc).replaceAll(mapping.regex, mapping.soapURI));
	}

	private void transformAndReplaceBody(Message msg, String ss, Source src) throws Exception {
		String soapEnv = xsltTransformer.transform(ss, src);
		log.debug("soap-env: " + soapEnv);
		msg.setBodyContent(soapEnv.getBytes("UTF-8"));
	}

	private String getURI(AbstractExchange exc) {
		return exc.getRequest().getUri();
	}

	public List<Mapping> getMappings() {
		return mappings;
	}

	public void setMappings(List<Mapping> mappings) {
		this.mappings = mappings;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("rest2Soap");

		for (Mapping m : mappings) {
			out.writeStartElement("mapping");

			out.writeAttribute("regex", m.regex);
			out.writeAttribute("soapAction", m.soapAction);
			out.writeAttribute("soapURI", m.soapURI);
			out.writeAttribute("requestXSLT", m.requestXSLT);
			out.writeAttribute("responseXSLT", m.responseXSLT);

			out.writeEndElement();
		}

		out.writeEndElement();
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws XMLStreamException {
		if (token.getLocalName().equals("mapping")) {
			GenericConfigElement mapping = new GenericConfigElement();
			mapping.parse(token);
			mappings.add(new Mapping(mapping.getAttribute("regex"), 
												           mapping.getAttribute("soapAction"),
												           mapping.getAttribute("soapURI"),
												           mapping.getAttribute("requestXSLT"),
												           mapping.getAttribute("responseXSLT")));
		} else {
			super.parseChildren(token, child);
		}
	}
}
