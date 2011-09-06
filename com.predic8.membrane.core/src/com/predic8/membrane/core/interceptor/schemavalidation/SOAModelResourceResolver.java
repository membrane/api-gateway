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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import com.predic8.schema.*;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class SOAModelResourceResolver implements LSResourceResolver {

	private static Log log = LogFactory.getLog(SOAModelResourceResolver.class.getName());
	
	public List<Schema> schemas;
	
	public void loadFromWSDL(String wsdl) {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		schemas = getAllSchemas(ctx);		
	}
	
	public void loadFromSchema(String schema) {
		schemas = (List<Schema>) new SchemaParser().parse(schema).getAllSchemas();
	}

	private List<Schema> getAllSchemas(WSDLParserContext ctx) {
		return new WSDLParser().parse(ctx).getTypes().getAllSchemas();
	}
	
	@Override
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
		for (Schema schema : schemas) {
			if (namespaceURI.equals(schema.getTargetNamespace()))
				return new LSInputImpl(publicId, systemId, schema.getAsString());
		}
		log.error("No matching schema found for target namespace: " + namespaceURI);
		return null;
	}
}
