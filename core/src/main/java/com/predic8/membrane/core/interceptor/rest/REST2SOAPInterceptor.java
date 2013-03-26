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
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.MediaType;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.xslt.XSLTTransformer;
import com.predic8.membrane.core.rules.AbstractServiceProxy;

@MCElement(name="rest2Soap", xsd="" +
		"					<xsd:sequence>\r\n" + 
		"						<xsd:element name=\"mapping\" minOccurs=\"1\" maxOccurs=\"unbounded\">\r\n" + 
		"							<xsd:complexType>\r\n" + 
		"								<xsd:sequence />\r\n" + 
		"								<xsd:attribute name=\"regex\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"								<xsd:attribute name=\"soapAction\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"								<xsd:attribute name=\"soapURI\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"								<xsd:attribute name=\"requestXSLT\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"								<xsd:attribute name=\"responseXSLT\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"							</xsd:complexType>\r\n" + 
		"						</xsd:element>\r\n" + 
		"					</xsd:sequence>\r\n" + 
		"", generateParserClass=false)
public class REST2SOAPInterceptor extends AbstractInterceptor {

	public static class Mapping extends AbstractXmlElement {
		public String regex;
		public String soapAction;
		public String soapURI;
		public String requestXSLT;
		public String responseXSLT;

		@Override
		protected void parseAttributes(XMLStreamReader token) throws Exception {
			regex = token.getAttributeValue("", "regex");
			soapAction = token.getAttributeValue("", "soapAction");
			soapURI = token.getAttributeValue("", "soapURI");
			requestXSLT = token.getAttributeValue("", "requestXSLT");
			responseXSLT = token.getAttributeValue("", "responseXSLT");
		}

		@Override
		public void write(XMLStreamWriter out) throws XMLStreamException {
			out.writeStartElement("mapping");

			out.writeAttribute("regex", regex);
			out.writeAttribute("soapAction", soapAction);
			out.writeAttribute("soapURI", soapURI);
			out.writeAttribute("requestXSLT", requestXSLT);
			out.writeAttribute("responseXSLT", responseXSLT);
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
		Mapping mapping = getRESTURL(exc);
		log.debug("restURL: " + mapping);
		if (getRESTURL(exc) == null)
			return Outcome.CONTINUE;

		if (log.isDebugEnabled())
			log.debug("response: " + new String(getTransformer(null).transform(getBodySource(exc)), Constants.UTF_8_CHARSET));

		exc.getResponse().setBodyContent(getTransformer(mapping.responseXSLT).
				transform(getBodySource(exc)));
		setContentType(exc.getResponse().getHeader());

		XML2HTTP.unwrapResponseIfNecessary(exc.getResponse());
		convertResponseToJSONIfNecessary(exc.getRequest().getHeader(), mapping, exc.getResponse());
		
		return Outcome.CONTINUE;
	}

	private static MediaType[] supportedTypes = Header.convertStringsToMediaType(new String[] { MimeType.TEXT_XML, MimeType.APPLICATION_JSON_UTF8 });
	
	private void convertResponseToJSONIfNecessary(Header requestHeader, Mapping mapping, Response response) throws IOException, Exception {
		boolean inputIsXml = MimeType.TEXT_XML_UTF8.equals(response.getHeader().getContentType());
		int wantedType = requestHeader.getBestAcceptedType(supportedTypes);
		if (inputIsXml && wantedType >= 1) {
			response.setBodyContent(xml2json(response.getBody().getContent()));
			setJSONContentType(response.getHeader());
		}
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
			t = new XSLTTransformer(ss, router, concurrency);
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
		header.setContentType(MimeType.APPLICATION_JSON_UTF8);
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
		return "http://" + ((AbstractServiceProxy) exc.getRule()).getTargetHost() + ":"
				+ ((AbstractServiceProxy) exc.getRule()).getTargetPort()
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
	public String getShortDescription() {
		return "Transforms REST requests into SOAP and responses vice versa.";
	}
	
	@Override
	public String getHelpId() {
		return "rest2soap";
	}
	
}
