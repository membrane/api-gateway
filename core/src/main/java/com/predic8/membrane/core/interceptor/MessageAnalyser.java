/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.multipart.*;
import org.slf4j.*;

import javax.xml.stream.*;
import java.io.*;

import static com.predic8.membrane.core.Constants.*;

@MCElement(name="analyser")
public class MessageAnalyser extends AbstractInterceptor {

	protected static final Logger log = LoggerFactory.getLogger(MessageAnalyser.class);

	public static final String REQUEST_ROOT_ELEMENT_NAME = "MEMRequestRootElementName";
	public static final String REQUEST_ROOT_ELEMENT_NS = "MEMRequestRootElementNS";
	public static final String REQUEST_SOAP_VERSION = "MEMRequestSoapVersion";
	public static final String REQUEST_SOAP_OPERATION = "MEMRequestSoapOperation";
	public static final String REQUEST_SOAP_OPERATION_NS = "MEMRequestSoapOperationNS";

	public static final String RESPONSE_ROOT_ELEMENT_NAME = "MEMResponseRootElementName";
	public static final String RESPONSE_ROOT_ELEMENT_NS = "MEMResponseRootElementNS";
	public static final String RESPONSE_SOAP_VERSION = "MEMResponseSoapVersion";
	public static final String RESPONSE_SOAP_OPERATION = "MEMResponseSoapOperation";
	public static final String RESPONSE_SOAP_OPERATION_NS = "MEMResponseSoapOperationNS";

	private static final XOPReconstitutor xopr = new XOPReconstitutor();
	private static final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	private static class ExtractedData {
		public String rootElementName;
		public String rootElementNS;
		public String bodyContentElementName;
		public String bodyContentElementNS;

		public Constants.SoapVersion getSoapVersion() {
			return SOAP11_NS.equals(rootElementNS) ? Constants.SoapVersion.SOAP11 : Constants.SoapVersion.SOAP12;
		}

		public boolean hasAnyData() {
			return rootElementName != null;
		}

		public boolean hasSoapData() {
			return "Envelope".equals(rootElementName) && (SOAP11_NS.equals(rootElementNS) || Constants.SOAP12_NS.equals(rootElementNS));
		}
	}

	public MessageAnalyser() {
		name = "message analyser";
	}

	@Override
	public String getLongDescription() {
		return getShortDescription();
	}

	@Override
	public String getShortDescription() {
		return "Extracts Information about the XML from the message.";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {

        ExtractedData data = null;
        try {
            data = analyse(exc.getRequest());
        } catch (Exception e) {
			log.error("Could not analyse request.", e);
        }

        if (!data.hasAnyData()) {
			return Outcome.CONTINUE;
		}

		exc.setProperty(REQUEST_ROOT_ELEMENT_NAME, data.rootElementName);
		exc.setProperty(REQUEST_ROOT_ELEMENT_NS, data.rootElementNS);

		if (!data.hasSoapData()) {
			return Outcome.CONTINUE;
		}

		exc.setProperty(REQUEST_SOAP_OPERATION, data.bodyContentElementName);
		exc.setProperty(REQUEST_SOAP_OPERATION_NS, data.bodyContentElementNS);
		exc.setProperty(REQUEST_SOAP_VERSION, data.getSoapVersion());

		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) {

        ExtractedData data = null;
        try {
            data = analyse(exc.getResponse());
        } catch (Exception e) {
            log.error("Could not analyse response.", e);
        }

        if (!data.hasAnyData()) {
			return Outcome.CONTINUE;
		}

		exc.setProperty(RESPONSE_ROOT_ELEMENT_NAME, data.rootElementName);
		exc.setProperty(RESPONSE_ROOT_ELEMENT_NS, data.rootElementNS);

		if (!data.hasSoapData()) {
			return Outcome.CONTINUE;
		}

		exc.setProperty(RESPONSE_SOAP_OPERATION, data.bodyContentElementName.replace("Response", ""));
		exc.setProperty(RESPONSE_SOAP_OPERATION_NS, data.bodyContentElementNS);
		exc.setProperty(RESPONSE_SOAP_VERSION, data.getSoapVersion());

		return Outcome.CONTINUE;
	}

	private ExtractedData analyse(Message msg) throws Exception {

		ExtractedData res = new ExtractedData();

		if (msg.isBodyEmpty() && !msg.isXML()) {
			return res;
		}

		XMLStreamReader reader = getReader(msg);

		extractRootNameAndNS(reader, res);

		if (!res.hasSoapData()) {
			return res;
		}

		moveToSoapBody(reader);

		extractBodyContentNameAndNS(reader, res);

		return res;
	}

	private void moveToTag(XMLStreamReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			reader.next();
			if (reader.isStartElement()) {
				return;
			}
		}
	}

	private XMLStreamReader getReader(Message msg) throws XMLStreamException,
	FactoryConfigurationError, IOException {
		synchronized (xmlInputFactory) {
			return xmlInputFactory.createXMLStreamReader(xopr.reconstituteIfNecessary(msg));
		}
	}

	private void extractRootNameAndNS(XMLStreamReader reader, ExtractedData res)
			throws XMLStreamException {
		moveToTag(reader);
		res.rootElementName = reader.getName().getLocalPart();
		res.rootElementNS = reader.getNamespaceURI();
	}

	private void extractBodyContentNameAndNS(XMLStreamReader reader, ExtractedData res)
			throws XMLStreamException {
		moveToTag(reader);
		res.bodyContentElementName = reader.getName().getLocalPart();
		res.bodyContentElementNS = reader.getNamespaceURI();
	}

	private void moveToSoapBody(XMLStreamReader reader) throws XMLStreamException {
		while (hasNextAndIsNotBody(reader) ) {
			moveToTag(reader);
		}
	}

	private boolean hasNextAndIsNotBody(XMLStreamReader reader)
			throws XMLStreamException {
		return reader.hasNext() &&
				!( reader.isStartElement() &&
						"Body".equals(reader.getName().getLocalPart()) &&
						SOAP11_NS.equals(reader.getNamespaceURI())
						);
	}
}
