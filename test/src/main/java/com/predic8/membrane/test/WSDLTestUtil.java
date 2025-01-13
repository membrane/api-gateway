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
package com.predic8.membrane.test;

import org.apache.http.*;

import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class WSDLTestUtil {
	private static final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	public static List<String> getXSDs(String wsdl) throws XMLStreamException {
		List<String> result = new ArrayList<>();

		XMLEventReader parser;
		synchronized(xmlInputFactory) {
			parser = xmlInputFactory.createXMLEventReader(new StringReader(wsdl));
		}

		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();
			if (event.isStartElement()) {
				String name = event.asStartElement().getName().getLocalPart();
				if (name.equals("import") || name.equals("include")) {
					Attribute a = event.asStartElement().getAttributeByName(new QName("schemaLocation"));
					if (a != null)
						result.add(a.getValue());
				}
			}
		}

		return result;
	}

	public static int countWSDLandXSDs(String url) throws ParseException, XMLStreamException, IOException {
		int sum = 1;
        for (String xsd : WSDLTestUtil.getXSDs(AssertUtils.getAndAssert200(url)))
			sum += countWSDLandXSDs(new URL(new URL(url), xsd).toString());
		return sum;
	}

}
