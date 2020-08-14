/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.ws_addressing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.predic8.membrane.core.exchange.Exchange;

public class DecoupledEndpointRewriter {
	private static final String ADDRESSING_URI = "http://www.w3.org/2005/08/addressing";

	private final DocumentBuilderFactory builderFactory;
	private final TransformerFactory transformerFactory = TransformerFactory.newInstance();
	private final DecoupledEndpointRegistry registry;

	public DecoupledEndpointRewriter(DecoupledEndpointRegistry registry) {
		builderFactory = DocumentBuilderFactory.newInstance();
		builderFactory.setNamespaceAware(true);

		this.registry = registry;
	}

	public void rewriteToElement(InputStream reader, OutputStream output, Exchange exc) throws ParserConfigurationException, IOException, SAXException, TransformerException {
		Document doc = getDocument(reader);
		String uri = getRelatesToValue(doc);
		setTarget(exc, uri);
		setToElement(doc, uri);
		writeDocument(output, doc);
	}

	private void setTarget(Exchange exc, String uri) {
		exc.getDestinations().set(0, registry.lookup(uri));
	}

	private void writeDocument(OutputStream output, Document doc) throws TransformerException {
		transformerFactory.newTransformer().transform(new DOMSource(doc), new StreamResult(output));
	}

	private Document getDocument(InputStream reader) throws SAXException, IOException, ParserConfigurationException {
		return builderFactory.newDocumentBuilder().parse(reader);
	}

	private void setToElement(Document doc, String relatesTo) {
		doc.getElementsByTagNameNS(ADDRESSING_URI, "To").item(0).setTextContent(registry.lookup(relatesTo));
	}

	private String getRelatesToValue(Document doc) {
		return doc.getElementsByTagNameNS(ADDRESSING_URI, "RelatesTo").item(0).getTextContent();
	}
}