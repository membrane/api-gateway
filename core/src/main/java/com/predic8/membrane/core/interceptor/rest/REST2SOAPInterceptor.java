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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import org.slf4j.*;
import org.springframework.http.*;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;


/**
 * @description Converts REST requests into SOAP messages.
 * @topic 8. SOAP based Web Services
 */
@MCElement(name="rest2Soap")
public class REST2SOAPInterceptor extends SOAPRESTHelper {

	@MCElement(name="mapping", topLevel=false, id="rest2Soap-mapping")
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

		public String getRegex() {
			return regex;
		}

		/**
		 * @description Java Regular expression
		 * @example /bank/.*
		 */
		@Required
		@MCAttribute
		public void setRegex(String regex) {
			this.regex = regex;
		}

		public String getSoapAction() {
			return soapAction;
		}

		/**
		 * @description Value of the soapAction header field.
		 */
		@Required
		@MCAttribute
		public void setSoapAction(String soapAction) {
			this.soapAction = soapAction;
		}

		@SuppressWarnings("unused")
		public String getSoapURI() {
			return soapURI;
		}

		/**
		 * @description Endpoint address of the SOAP service.
		 * @example /axis2/$1
		 */
		@Required
		@MCAttribute
		public void setSoapURI(String soapURI) {
			this.soapURI = soapURI;
		}

		public String getRequestXSLT() {
			return requestXSLT;
		}

		/**
		 * @description Transformation that will be applied to the request.
		 * @example blz-request.xsl
		 */
		@Required
		@MCAttribute
		public void setRequestXSLT(String requestXSLT) {
			this.requestXSLT = requestXSLT;
		}

		public String getResponseXSLT() {
			return responseXSLT;
		}

		/**
		 * @description Transformation that will be applied to the response.
		 * @example shop-request.xsl
		 */
		@Required
		@MCAttribute
		public void setResponseXSLT(String responseXSLT) {
			this.responseXSLT = responseXSLT;
		}
	}

	private static final Logger log = LoggerFactory.getLogger(REST2SOAPInterceptor.class.getName());

	private List<Mapping> mappings = new ArrayList<>();
	private Boolean isSOAP12;

	public REST2SOAPInterceptor() {
		name = "REST 2 SOAP Gateway";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("uri: " + getURI(exc));
		String uri = getURI(exc);

		Mapping mapping = findFirstMatchingRegEx(uri);
		if (mapping == null)
			return CONTINUE;

		transformAndReplaceBody(exc.getRequest(), mapping.requestXSLT,
				getRequestXMLSource(exc), exc.getStringProperties());
		modifyRequest(exc, mapping);

		return CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		Mapping mapping = getRESTURL(exc);
		log.debug("restURL: " + mapping);
		if (getRESTURL(exc) == null)
			return CONTINUE;

		if (log.isDebugEnabled())
			log.debug("response: " + new String(getTransformer(null).transform(getBodySource(exc), exc.getStringProperties()), UTF_8));

		exc.getResponse().setBodyContent(getTransformer(mapping.responseXSLT).
				transform(getBodySource(exc)));
		Header header = exc.getResponse().getHeader();
		header.removeFields(CONTENT_TYPE);
		header.setContentType(TEXT_XML_UTF8);

		XML2HTTP.unwrapMessageIfNecessary(exc.getResponse());
		convertResponseToJSONIfNecessary(exc.getRequest().getHeader(), exc.getResponse(), exc.getStringProperties());

		return CONTINUE;
	}

	private void convertResponseToJSONIfNecessary(Header header, Response response, Map<String, String> properties) throws Exception {
		if (!response.isXML())
			return;

		String accept = header.getFirstValue(ACCEPT);
		if (accept == null)
			return;

		List<MediaType> types = sortMimeTypeByQualityFactorAscending(accept);
		if (types.isEmpty())
			return;

		if (!isJson(types.get(0)))
			return;

		response.setBodyContent(xml2json(response.getBodyAsStreamDecoded(), properties));
		setJSONContentType(response.getHeader());
	}

	private byte[] xml2json(InputStream xmlResp, Map<String, String> properties) throws Exception {
		return getTransformer("classpath:/com/predic8/membrane/core/interceptor/rest/xml2json.xsl").
				transform(new StreamSource(xmlResp), properties);
	}

	private StreamSource getBodySource(Exchange exc) {
		return new StreamSource(exc.getResponse().getBodyAsStreamDecoded());
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
		Header header = exc.getRequest().getHeader();
		header.removeFields(CONTENT_TYPE);
		header.setContentType(isSOAP12(exc) ? APPLICATION_SOAP : TEXT_XML_UTF8);

		exc.setProperty("mapping", mapping);
		setServiceEndpoint(exc, mapping);
	}

	private boolean isSOAP12(AbstractExchange exc) {
		if (isSOAP12 != null)
			return isSOAP12;
		isSOAP12 = SOAP12_NS.equals(getRootElementNamespace(exc.getRequest().getBodyAsStream()));
		return isSOAP12;
	}

	private String getRootElementNamespace(InputStream stream) {
		try {
			XMLEventReader xer = XMLInputFactory.newFactory().createXMLEventReader(stream);
			while (xer.hasNext()) {
				XMLEvent event = xer.nextEvent();
				if (event.isStartElement())
					return event.asStartElement().getName().getNamespaceURI();
			}
		} catch (XMLStreamException e) {
			log.error("Could not determine root element namespace for check whether namespace is SOAP 1.2.", e);
		}
		return null;
	}

	private void setJSONContentType(Header header) {
		header.removeFields(CONTENT_TYPE);
		header.setContentType(APPLICATION_JSON_UTF8);
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
		return getProtocol(exc) + "://" + ((AbstractServiceProxy) exc.getRule()).getTargetHost() + ":"
				+ ((AbstractServiceProxy) exc.getRule()).getTargetPort()
				+ exc.getRequest().getUri();
	}

	private String getProtocol(AbstractExchange exc) {
		if(exc.getRule().getSslOutboundContext() != null) {
			return "https";
		}
		return "http";
	}

	private String getURI(AbstractExchange exc) {
		return exc.getRequest().getUri();
	}

	public List<Mapping> getMappings() {
		return mappings;
	}

	/**
	 * @description Specifies the mappings. The first matching mapping will be applied to the request.
	 */
	@MCChildElement
	public void setMappings(List<Mapping> mappings) {
		this.mappings = mappings;
	}

	@Override
	public String getShortDescription() {
		return "Transforms REST requests into SOAP and responses vice versa.";
	}
}