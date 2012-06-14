package com.predic8.membrane.core.interceptor.soap;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.log4j.Logger;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class SoapOperationExtractor extends AbstractInterceptor {

	private static final Logger log = Logger.getLogger(SoapOperationExtractor.class);
	
	public static final String SOAP_OPERATION = "XSLT_SOAP_OPERATION";
	public static final String SOAP_OPERATION_NS = "XSLT_SOAP_OPERATION_NS";
	
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

	private XMLStreamReader getReader(Exchange exc) throws XMLStreamException,
			FactoryConfigurationError {
		return XMLInputFactory.newInstance().createXMLStreamReader(exc.getRequest().getBodyAsStream());
	}

	private boolean isNotSoap(XMLStreamReader reader) throws Exception {
		reader.nextTag();
		return !("Envelope".equals(reader.getName().getLocalPart()) && "http://schemas.xmlsoap.org/soap/envelope/".equals(reader.getNamespaceURI()));
	}

	private void extractAndSaveNameAndNS(Exchange exc, XMLStreamReader reader)
			throws XMLStreamException {
		reader.nextTag();
		exc.setProperty(SOAP_OPERATION, reader.getName().getLocalPart());
		exc.setProperty(SOAP_OPERATION_NS, reader.getNamespaceURI());
	}

	private void moveToSoapBody(XMLStreamReader reader) throws XMLStreamException {
		while (hasNextAndIsNotBody(reader) ) {
			reader.nextTag();
		}
	}

	private boolean hasNextAndIsNotBody(XMLStreamReader reader)
			throws XMLStreamException {
		return reader.hasNext() && 
			   !( reader.isStartElement() &&
			     "Body".equals(reader.getName().getLocalPart()) &&
			     "http://schemas.xmlsoap.org/soap/envelope/".equals(reader.getNamespaceURI())
			    );
	}	
}
