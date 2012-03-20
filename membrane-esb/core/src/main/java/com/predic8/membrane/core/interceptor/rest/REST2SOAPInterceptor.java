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
package com.predic8.membrane.core.interceptor.rest;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.xslt.XSLTTransformer;
import com.predic8.membrane.core.rules.ServiceProxy;

public class REST2SOAPInterceptor extends AbstractInterceptor {

	public static class Mapping extends AbstractXmlElement {
		public String regex;
		public String soapAction;
		public String soapURI;
		public String requestXSLT;
		public String responseXSLT;
		public String responseType = "xml";

		@Override
		protected void parseAttributes(XMLStreamReader token) throws Exception {
			regex = token.getAttributeValue("", "regex");
			soapAction = token.getAttributeValue("", "soapAction");
			soapURI = token.getAttributeValue("", "soapURI");
			requestXSLT = token.getAttributeValue("", "requestXSLT");
			responseXSLT = token.getAttributeValue("", "responseXSLT");
			if (token.getAttributeValue("", "responseType") != null) {
				responseType = token.getAttributeValue("", "responseType");
			}

		}

		@Override
		public void write(XMLStreamWriter out) throws XMLStreamException {
			out.writeStartElement("mapping");

			out.writeAttribute("regex", regex);
			out.writeAttribute("soapAction", soapAction);
			out.writeAttribute("soapURI", soapURI);
			out.writeAttribute("requestXSLT", requestXSLT);
			out.writeAttribute("responseXSLT", responseXSLT);
			if (!"xml".equals(responseType)) {
				out.writeAttribute("responseType", responseType);
			}
			out.writeEndElement();
		}
	}

	private static Log log = LogFactory.getLog(REST2SOAPInterceptor.class
			.getName());

	private List<Mapping> mappings = new ArrayList<Mapping>();
	private final ConcurrentHashMap<String, XSLTTransformer> xsltTransformers = 
			new ConcurrentHashMap<String, XSLTTransformer>();

	public REST2SOAPInterceptor() {
		name = "REST 2 SOAP Gateway";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("uri: " + getURI(exc));
		String uri = getURI(exc);

		Mapping mapping = findFirstMatchingRegEx(uri);
		if (mapping == null)
			return Outcome.CONTINUE;

		transformAndReplaceBody(exc.getRequest(), mapping.requestXSLT,
				getRequestXMLSource(exc));
		modifyRequest(exc, mapping);

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.debug("restURL: " + getRESTURL(exc));
		if (getRESTURL(exc) == null)
			return Outcome.CONTINUE;

		if (log.isDebugEnabled())
			log.debug("response: " + new String(getTransformer(null).transform(getBodySource(exc)), Constants.UTF_8_CHARSET));

		byte[] transformedResp = getTransformer(getRESTURL(exc).responseXSLT).
				transform(getBodySource(exc));
		setContentType(exc.getResponse().getHeader());

		if ("json".equals(getRESTURL(exc).responseType)) {
			transformedResp = xml2json(transformedResp);
			setJSONContentType(exc.getResponse().getHeader());
		}
		exc.getResponse().setBodyContent(transformedResp);
		return Outcome.CONTINUE;
	}

	private byte[] xml2json(byte[] xmlResp) throws Exception {
		return getTransformer("classpath:/com/predic8/membrane/core/interceptor/rest/xml2json.xsl").
				transform(new StreamSource(new ByteArrayInputStream(xmlResp)));
	}

	private XSLTTransformer getTransformer(String ss) throws Exception {
		String key = ss == null ? "null" : ss;
		XSLTTransformer t = xsltTransformers.get(key);
		if (t == null) {
			int concurrency = 2 * Runtime.getRuntime().availableProcessors();
			t = new XSLTTransformer(ss, router.getResourceResolver(), concurrency);
			XSLTTransformer t2 = xsltTransformers.putIfAbsent(key, t);
			if (t2 != null)
				return t2;
		}
		return t;
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
		header.setContentType(MimeType.TEXT_XML_UTF8);
	}

	private void setJSONContentType(Header header) {
		header.removeFields(Header.CONTENT_TYPE);
		header.setContentType(MimeType.JSON);
	}

	private void setServiceEndpoint(AbstractExchange exc, Mapping mapping) {
		exc.getRequest().setUri(
				getURI(exc).replaceAll(mapping.regex, mapping.soapURI));

		String newDestination = getNewDestination(exc);
		exc.getDestinations().clear();
		exc.getDestinations().add(newDestination);

		log.debug("destination set to: " + newDestination);
	}

	private String getNewDestination(AbstractExchange exc) {
		return "http://" + ((ServiceProxy) exc.getRule()).getTargetHost() + ":"
				+ ((ServiceProxy) exc.getRule()).getTargetPort()
				+ exc.getRequest().getUri();
	}

	private void transformAndReplaceBody(Message msg, String ss, Source src)
			throws Exception {
		byte[] soapEnv = getTransformer(ss).transform(src);
		if (log.isDebugEnabled())
			log.debug("soap-env: " + new String(soapEnv, Constants.UTF_8_CHARSET));
		msg.setBodyContent(soapEnv);
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
			m.write(out);
		}

		out.writeEndElement();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("mapping")) {
			mappings.add((Mapping) new Mapping().parse(token));
		} else {
			super.parseChildren(token, child);
		}
	}
}
