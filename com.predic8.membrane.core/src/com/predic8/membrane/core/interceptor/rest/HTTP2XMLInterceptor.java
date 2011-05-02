package com.predic8.membrane.core.interceptor.rest;

import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class HTTP2XMLInterceptor extends AbstractInterceptor {

	/*
	 * private class RequestBuilder { private String method; private String uri;
	 * 
	 * private String build() throws Exception { StringWriter sw = new
	 * StringWriter(); XMLStreamWriter w =
	 * XMLOutputFactory.newInstance().createXMLStreamWriter(sw);
	 * 
	 * w.writeStartDocument(); w.writeStartElement("request");
	 * w.writeStartElement("metadata"); writeSimpleElement(w,"method",method);
	 * writeSimpleElement(w,"uri",uri); w.writeStartElement("uri-details");
	 * writeSimpleElement(w,"schema",getScheme());
	 * writeSimpleElement(w,"host",getHost()); writePort(w); writePath(w);
	 * writeQuery(w); w.writeEndElement(); w.writeEndElement();
	 * w.writeEndElement(); w.writeEndDocument();
	 * 
	 * return sw.toString(); }
	 * 
	 * 
	 * private void writePort(XMLStreamWriter w) throws Exception { int port =
	 * new URI(uri).getPort(); if ( port == -1 ) return; writeSimpleElement(w,
	 * "port", ""+port); }
	 * 
	 * 
	 * private String getHost() throws Exception { return new
	 * URI(uri).getHost(); }
	 * 
	 * 
	 * private String getScheme() throws Exception { return new
	 * URI(uri).getScheme(); }
	 * 
	 * 
	 * private void writePath(XMLStreamWriter w) throws Exception { String path
	 * = new URI(uri).getPath(); if (path == null ) return;
	 * w.writeStartElement("path"); for ( String c : path.split("/")) { if (
	 * "".equals(c) ) continue; writeSimpleElement(w, "component", c); }
	 * w.writeEndElement(); }
	 * 
	 * private void writeQuery(XMLStreamWriter w) throws Exception { String
	 * query = new URI(uri).getQuery(); if (query == null ) return;
	 * w.writeStartElement("query"); w.writeEndElement(); }
	 * 
	 * private void writeSimpleElement(XMLStreamWriter w, String name, String
	 * value) throws XMLStreamException { w.writeStartElement(name);
	 * w.writeCharacters(value); w.writeEndElement(); }
	 * 
	 * public void setMethod(String method) { this.method = method; }
	 * 
	 * public void setURI(String uri) { this.uri = uri; } }
	 */

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		System.out.println(exc.getRequest().getUri());
		
		Request req = new Request();
		
		req.setMethod("GET");
		req.setHttpVersion("1.1");
		
		String res = req.toXml();
		System.out.println(res);

		exc.getRequest().setBodyContent(res.getBytes("UTF-8"));
		return Outcome.CONTINUE;
	}

}
