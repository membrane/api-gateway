/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.schemavalidation;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import static com.predic8.membrane.core.util.SOAPUtil.isSOAPNamespace;

/**
 * Strips the SOAP envelope so that only the payload below {@code <soap:Body>} is passed on.
 * The {@code Body} boundary is matched by namespace, not just local name: a domain element
 * that happens to be named {@code Body} in another namespace is left untouched and forwarded.
 * <p>
 * A second SOAP {@code Header}, or one after the {@code Body}, is reported as an error to the
 * error handler, so a validator reading this filter rejects the message: SOAP allows one header,
 * in front of the body.
 */
public class SOAPXMLFilter extends XMLFilterImpl {

	private static final int ENVELOPE_CHILD_DEPTH = 2;

	/**
	 * Where the parser is among the children of the envelope.
	 */
	private enum Position {
		START,
		/**
		 * A header has started, the body has not.
		 */
		AFTER_HEADER,
		IN_BODY,
		AFTER_BODY
	}

	private Position position = Position.START;

	/**
	 * Depth of the current element: {@code 1} for the envelope, {@code 2} for its children.
	 */
	private int depth;

	private Locator locator;

	public SOAPXMLFilter(XMLReader reader) {
		super(reader);
	}

	private static boolean isSoapBody(String uri, String localName) {
		return localName.equals("Body") && isSOAPNamespace(uri);
	}

	private static boolean isSoapHeader(String uri, String localName) {
		return localName.equals("Header") && isSOAPNamespace(uri);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
		super.setDocumentLocator(locator);
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		depth++;

		if (isSoapBody(uri, localName)) {
			position = Position.IN_BODY;
			return;
		}

		if (depth == ENVELOPE_CHILD_DEPTH && isSoapHeader(uri, localName))
			startHeader();

		if (position != Position.IN_BODY)
			return;

		super.startElement(uri, localName, qName, atts);

	}

	/**
	 * SOAP allows one header, in front of the body. Inside the body, no header is a child of the
	 * envelope.
	 */
	private void startHeader() throws SAXException {
		switch (position) {
			case START -> position = Position.AFTER_HEADER;
			case AFTER_HEADER -> error(new SAXParseException("The SOAP Envelope must not contain more than one Header.", locator));
			case AFTER_BODY -> error(new SAXParseException("The SOAP Header must come before the Body.", locator));
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		depth--;

		if (isSoapBody(uri, localName)) {
			position = Position.AFTER_BODY;
			return;
		}

		if (position != Position.IN_BODY)
			return;

		super.endElement(uri, localName, qName);
	}
}
