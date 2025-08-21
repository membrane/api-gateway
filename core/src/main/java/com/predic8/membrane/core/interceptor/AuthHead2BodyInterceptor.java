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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;

@MCElement(name="authHead2Body")
public class AuthHead2BodyInterceptor extends AbstractInterceptor {
	static final String COM_NS  = "test";
	static final String NOR_NS  = "http://cooreo.com.br/normandes-EnviaSMS";
	static final String XSI_NS  = "http://www.w3.org/2001/XMLSchema-instance";

	public Outcome handleRequest(AbstractExchange exchange) throws Exception {
        Document doc = getDocument(exchange.getRequest().getBodyAsStreamDecoded(), getEncodingOrDefault(exchange.getRequest()));
		Element header = getAuthorisationHeader(doc);
		if (header == null) return CONTINUE;
		Element nor = getNorElement(doc);
		nor.appendChild(getUsername(doc));
		nor.appendChild(getPassword(doc));

		header.getParentNode().removeChild(header);
		exchange.getRequest().setBody(new Body(DOM2String(doc).getBytes(getEncodingOrDefault(exchange.getRequest()))));
		return CONTINUE;
	}

	private static @NotNull String getEncodingOrDefault(Message message) {
		return requireNonNullElseGet(message.getCharset(), UTF_8::name);
	}

	private Node getPassword(Document doc) {
		Element e = doc.createElement("password");
		e.appendChild(doc.createTextNode(doc.getElementsByTagNameNS(COM_NS, "password").item(0).getTextContent()));
		e.setAttributeNS(XSI_NS, "xsi:type", "xsd:string");
		return e;
	}

	private Node getUsername(Document doc) {
		Element e = doc.createElement("username");
		e.appendChild(doc.createTextNode(doc.getElementsByTagNameNS(COM_NS, "userName").item(0).getTextContent()));
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

	private Document getDocument(InputStream xmlDocument, String encoding) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		InputSource is = new InputSource(xmlDocument);
		if (encoding != null)
			is.setEncoding(encoding);
		return dbf.newDocumentBuilder().parse(is);
	}

	private String DOM2String(Document doc) throws Exception {
		Transformer xformer = TransformerFactory.newInstance().newTransformer();
		StringWriter writer = new StringWriter();
		xformer.transform(new DOMSource(doc),new StreamResult(writer));
		return writer.toString();
	}

}

