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

public class SOAPXMLFilter extends XMLFilterImpl {


	private boolean body;

	public SOAPXMLFilter(XMLReader reader) {
		super(reader);
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {

		if (localName.equals("Body")) {
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

		if (localName.equals("Body")) {
			body = false;
			return;
		}

		if (!body)
			return;

		super.endElement(uri, localName, qName);
	}

	//	@Override
	//	public void startPrefixMapping(String prefix, String uri) throws SAXException {
	////		System.err.println("prefix: " + prefix);
	////		System.err.println("uri: " + uri);
	//		if (!"soapenv".equals(prefix)) {
	//
	//		}
	//		super.startPrefixMapping(prefix, uri);
	//	}
}
