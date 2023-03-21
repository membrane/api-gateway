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

package com.predic8.membrane.core.interceptor.soap;

import java.io.IOException;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;

import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name="soapOperationExtractor")
public class SoapOperationExtractor extends AbstractInterceptor {
	public static final String SOAP_OPERATION = "XSLT_SOAP_OPERATION";
	public static final String SOAP_OPERATION_NS = "XSLT_SOAP_OPERATION_NS";

	private static final XOPReconstitutor xopr = new XOPReconstitutor();
	private static final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	public SoapOperationExtractor() {
		name = "SOAP Operation Extractor";
	}

	@Override
	public String getLongDescription() {
		return getShortDescription();
	}

	@Override
	public String getShortDescription() {
		return "Saves SOAP operation name and namespace into exchange properties.";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		if (exc.getRequest().isBodyEmpty() && !exc.getRequest().isXML()) {
			return CONTINUE;
		}

		XMLStreamReader reader = getReader(exc);

		if (isNotSoap(reader)) {
			return CONTINUE;
		}

		moveToSoapBody(reader);

		extractAndSaveNameAndNS(exc, reader);

		return CONTINUE;
	}

	private void moveToTag(XMLStreamReader reader) throws XMLStreamException {
		while (reader.hasNext()) {
			reader.next();
			if (reader.isStartElement()) {
				return;
			}
		}
	}

	private XMLStreamReader getReader(Exchange exc) throws XMLStreamException,
	FactoryConfigurationError, IOException {
		synchronized (xmlInputFactory) {
			return xmlInputFactory.createXMLStreamReader(xopr.reconstituteIfNecessary(exc.getRequest()));
		}
	}

	private boolean isNotSoap(XMLStreamReader reader) throws XMLStreamException {
		moveToTag(reader);
		return !("Envelope".equals(reader.getName().getLocalPart()) && Constants.SOAP11_NS.equals(reader.getNamespaceURI()));
	}

	private void extractAndSaveNameAndNS(Exchange exc, XMLStreamReader reader)
			throws XMLStreamException {
		moveToTag(reader);
		exc.setProperty(SOAP_OPERATION, reader.getName().getLocalPart());
		exc.setProperty(SOAP_OPERATION_NS, reader.getNamespaceURI());
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
						Constants.SOAP11_NS.equals(reader.getNamespaceURI())
						);
	}
}
