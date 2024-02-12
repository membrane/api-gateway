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

package com.predic8.membrane.core.util;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.multipart.XOPReconstitutor;

public class SOAPUtil {
	private static final Logger log = LoggerFactory.getLogger(SOAPUtil.class.getName());

	public static boolean isSOAP(XMLInputFactory xmlInputFactory, XOPReconstitutor xopr, Message msg) {
		try {
			XMLEventReader parser;
			synchronized (xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(xopr.reconstituteIfNecessary(msg));
			}

			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();
				if (event.isStartElement()) {
					QName name = ((StartElement) event).getName();
					return (Constants.SOAP11_NS.equals(name.getNamespaceURI())
							|| Constants.SOAP12_NS.equals(name.getNamespaceURI())) &&
							"Envelope".equals(name.getLocalPart());
				}
			}
		} catch (Exception e) {
			log.warn("Ignoring exception: ", e);
		}
		return false;
	}


	public static boolean isFault(XMLInputFactory xmlInputFactory, XOPReconstitutor xopr, Message msg) {
		int state = 0;
		/*
		 * 0: waiting for "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
		 * 1: waiting for "<soapenv:Body>" (skipping any "<soapenv:Header>")
		 * 2: waiting for "<soapenv:Fault>"
		 */
		try {
			XMLEventReader parser;
			synchronized (xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(xopr
						.reconstituteIfNecessary(msg));
			}

			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();
				if (event.isStartElement()) {
					QName name = ((StartElement) event).getName();
					if (!Constants.SOAP11_NS.equals(name.getNamespaceURI())
							&& !Constants.SOAP12_NS.equals(name
									.getNamespaceURI()))
						return false;

					if ("Header".equals(name.getLocalPart())) {
						// skip header
						int stack = 0;
						while (parser.hasNext()) {
							event = parser.nextEvent();
							if (event.isStartElement())
								stack++;
							if (event.isEndElement())
								if (stack == 0)
									break;
								else
									stack--;
						}
						continue;
					}

					String expected;
					switch (state) {
						case 0 -> expected = "Envelope";
						case 1 -> expected = "Body";
						case 2 -> expected = "Fault";
						default -> {
							return false;
						}
					}
					if (expected.equals(name.getLocalPart())) {
						if (state == 2)
							return true;
						else
							state++;
					} else
						return false;
				}
				if (event.isEndElement())
					return false;
			}
		} catch (Exception e) {
			log.warn("Ignoring exception: ", e);
		}
		return false;
	}
}
