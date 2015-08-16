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
import java.util.ArrayList;

import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Source;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.util.SOAPUtil;
import com.predic8.schema.Schema;
import com.predic8.wsdl.Types;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class WSDLValidator extends AbstractXMLSchemaValidator {
	static Log log = LogFactory
			.getLog(WSDLValidator.class.getName());

	public WSDLValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) throws Exception {
		super(resourceResolver, location, failureHandler, skipFaults);
	}

	public WSDLValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) throws Exception {
		super(resourceResolver, location, failureHandler);
	}
	
	protected List<Schema> getSchemas() {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(location);
		try {
			WSDLParser wsdlParser = new WSDLParser();
			//System.out.println("Resolver----" + resourceResolver);
			wsdlParser.setResourceResolver(resourceResolver.toExternalResolver().toExternalResolver());
			List<Schema> schemaList = new ArrayList<Schema>();
			for (Types t : wsdlParser.parse(ctx).getTypes())
				schemaList.addAll(t.getSchemas());
			return schemaList;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Could not download the WSDL " + location + " or its dependent XML Schemas.", e);
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
		return SOAPUtil.isFault(xmlInputFactory, xopr, msg);
	}
	
	@Override
	protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
		if (SOAPUtil.isSOAP(xmlInputFactory, xopr, msg))
			return null;
		return "Not a SOAP message.";
	}
}
