package com.predic8.membrane.core.interceptor;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.multipart.XOPReconstitutor;

@MCElement(name="analyser")
public class MessageAnalyser extends AbstractInterceptor {
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
		
		public String getSoapVersion() {
			return Constants.SOAP11_NS.equals(rootElementNS)?Constants.SOAP11_VERION:Constants.SOAP12_NS;
		}
		
		public boolean hasAnyData() {
			return rootElementName != null;
		}

		public boolean hasSoapData() {
			return "Envelope".equals(rootElementName) && (Constants.SOAP11_NS.equals(rootElementNS) || Constants.SOAP12_NS.equals(rootElementNS));
		}
	}

	public MessageAnalyser() {
		name = "Message Analyser";		
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
	public Outcome handleRequest(Exchange exc) throws Exception {
		
		ExtractedData data = analyse(exc.getRequest());
		
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
	public Outcome handleResponse(Exchange exc) throws Exception {
		
		ExtractedData data = analyse(exc.getResponse());
		
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
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("analyser");
		out.writeEndElement();
	}	

	private ExtractedData analyse(Message msg) throws Exception {
		
		ExtractedData res = new ExtractedData();
		
		if ( msg.isBodyEmpty() && !msg.isXML()) {
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
			FactoryConfigurationError {
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
			     Constants.SOAP11_NS.equals(reader.getNamespaceURI())
			    );
	}	
}
