/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.http.Body;

public class AuthHead2BodyInterceptor extends AbstractInterceptor {
	static final String COM_NS  = "test";
	static final String NOR_NS  = "http://cooreo.com.br/normandes-EnviaSMS";
	static final String XSI_NS  = "http://www.w3.org/2001/XMLSchema-instance";
	
	public Outcome handleRequest(AbstractExchange exchange) throws Exception {
		Document doc = getDocument(exchange.getRequest().getBodyAsStream());
		Element header = getAuthorisationHeader(doc);
		if (header == null) return Outcome.CONTINUE;
		System.out.println(DOM2String(doc));
		Element nor = getNorElement(doc);
		nor.appendChild(getUsername(doc, header));
		nor.appendChild(getPassword(doc, header));		

		header.getParentNode().removeChild(header);		
		exchange.getRequest().setBody(new Body(DOM2String(doc).getBytes(exchange.getRequest().getCharset())));
		return Outcome.CONTINUE;
	}

	private Node getPassword(Document doc, Element header) {
		Element e = doc.createElement("password");
		e.appendChild(doc.createTextNode(((Element)doc.getElementsByTagNameNS(COM_NS, "password").item(0)).getTextContent()));
		e.setAttributeNS(XSI_NS, "xsi:type", "xsd:string");
		return e;
	}

	private Node getUsername(Document doc, Element header) {
		Element e = doc.createElement("username");
		e.appendChild(doc.createTextNode(((Element)doc.getElementsByTagNameNS(COM_NS, "userName").item(0)).getTextContent()));
		e.setAttributeNS(XSI_NS, "xsi:type", "xsd:string");
		return e;
	}

	private Element getNorElement(Document doc) {
		return (Element)doc.getElementsByTagNameNS(NOR_NS, "request").item(0);
	}

	private Element getAuthorisationHeader(Document doc) {
		NodeList nl = doc.getElementsByTagNameNS(COM_NS, "authorization");
		if (nl.getLength()==0) return null;
		return (Element)nl.item(0);
	}
	
	private Document getDocument(InputStream xmlDocument) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		return dbf.newDocumentBuilder().parse(new InputSource(xmlDocument));		
	}
	
	private String DOM2String(Document doc) throws Exception {
		Transformer xformer = TransformerFactory.newInstance().newTransformer();
		StringWriter writer = new StringWriter();
		xformer.transform(new DOMSource(doc),new StreamResult(writer)); 				
		return writer.toString();
	}
		
}

