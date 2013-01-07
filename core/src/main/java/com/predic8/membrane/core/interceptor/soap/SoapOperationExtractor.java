package com.predic8.membrane.core.interceptor.soap;

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
		
		if ( exc.getRequest().isBodyEmpty() && !exc.getRequest().isXML()) {
			return Outcome.CONTINUE;
		}
		
		XMLStreamReader reader = getReader(exc);
		
		if (isNotSoap(reader)) {
			return Outcome.CONTINUE;
		}
		
		moveToSoapBody(reader);
		
		extractAndSaveNameAndNS(exc, reader);

		return Outcome.CONTINUE;
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
			FactoryConfigurationError {
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
