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
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import static com.predic8.membrane.core.util.SOAPUtil.isSOAPNamespace;

/**
 * Strips the SOAP envelope so that only the payload below {@code <soap:Body>} is passed on.
 * The {@code Body} boundary is matched by namespace, not just local name: a domain element
 * that happens to be named {@code Body} in another namespace is left untouched and forwarded.
 * Only the {@code Body} that is a child of the envelope counts.
 * <p>
 * The structure of the envelope is not checked here, but before, see
 * {@link com.predic8.membrane.core.util.SOAPUtil#validateSOAPEnvelope}.
 */
public class SOAPXMLFilter extends XMLFilterImpl {

	private static final int ENVELOPE_CHILD_DEPTH = 2;

	/**
	 * Depth of the current element: {@code 1} for the envelope, {@code 2} for its children.
	 */
	private int depth;

	private boolean body;

	public SOAPXMLFilter(XMLReader reader) {
		super(reader);
	}

	private static boolean isSoapBody(String uri, String localName) {
		return localName.equals("Body") && isSOAPNamespace(uri);
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		depth++;

		if (depth == ENVELOPE_CHILD_DEPTH && isSoapBody(uri, localName)) {
			body = true;
			return;
		}

		if (!body)
			return;

		super.startElement(uri, localName, qName, atts);

	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		final var closing = depth--;

		if (closing == ENVELOPE_CHILD_DEPTH && isSoapBody(uri, localName)) {
			body = false;
			return;
		}

		if (!body)
			return;

		super.endElement(uri, localName, qName);
	}
}
