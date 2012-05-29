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

package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Source;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.util.ResourceResolver;
import com.predic8.schema.Schema;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;
import com.predic8.xml.util.ResourceDownloadException;

public class WSDLValidator extends AbstractXMLSchemaValidator {
	static Log log = LogFactory
			.getLog(WSDLValidator.class.getName());

	public WSDLValidator(ResourceResolver resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) throws Exception {
		super(resourceResolver, location, failureHandler, skipFaults);
	}

	public WSDLValidator(ResourceResolver resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) throws Exception {
		super(resourceResolver, location, failureHandler);
	}
	
	protected List<Schema> getSchemas() {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(location);
		try {
			WSDLParser wsdlParser = new WSDLParser();
			wsdlParser.setResourceResolver(resourceResolver.toExternalResolver());
			return wsdlParser.parse(ctx).getTypes().getSchemas();
		} catch (ResourceDownloadException e) {
			throw new IllegalArgumentException("Could not download the WSDL " + location + " or its dependent XML Schemas.");
		}
	}
	
	protected Source getMessageBody(InputStream input) throws Exception {
		return MessageUtil.getSOAPBody(input);
	}
	
	@Override
	protected Response createErrorResponse(String message) {
		return HttpUtil.createSOAPValidationErrorResponse(message);
	}

	private static XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
	static {
		xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	@Override
	protected boolean isFault(Message msg) {
		int state = 0;
		/*
		0: waiting for "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
		1: waiting for "<soapenv:Body>"   (skipping any "<soapenv:Header>")
		2: waiting for "<soapenv:Fault>"
		*/
		try {
			XMLEventReader parser;
			synchronized(xmlInputFactory) {
				parser = xmlInputFactory.createXMLEventReader(xopr.reconstituteIfNecessary(msg));
			}

			while (parser.hasNext()) {
				XMLEvent event = parser.nextEvent();
				if (event.isStartElement()) {
					QName name = ((StartElement)event).getName();
					if (!Constants.SOAP11_NS.equals(name.getNamespaceURI()) 
							&& !Constants.SOAP12_NS.equals(name.getNamespaceURI()))
						return false;
					
					if ("Header".equals(name.getLocalPart())) {
						// skip header
						int stack = 0;
						while (parser.hasNext()) {
							if (event.isStartElement())
								stack++;
							if (event.isEndElement())
								if (stack == 0)
									break;
								else
									stack--;
						}
					}
					
					String expected;
					switch (state) {
					case 0: expected = "Envelope"; break;
					case 1: expected = "Body"; break;
					case 2: expected = "Fault"; break;
					default: return false;
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
