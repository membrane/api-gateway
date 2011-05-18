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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLReaderFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.schema.Schema;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class ValidateSOAPMsgInterceptor extends AbstractInterceptor {
	
	private String wsdl;

	private TestErrorHandler handler;

	private List<Validator> validators;
	
	private SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
	
	public void init() throws Exception {
		handler = new TestErrorHandler();
		validators = getValidators();
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
			
		for (Validator validator: validators) {
			
			Source xmlSource = getSAXSource(exc.getRequest().getBodyAsStream()); 
			
			validator.validate(xmlSource);
			
			if (handler.getException() == null)
				return Outcome.CONTINUE;
			
			handler.reset();
		}
		
		exc.setResponse(HttpUtil.createSOAPFaultResponse("Validation failed."));
		
		return Outcome.ABORT;
	}

	public List<Validator> getValidators() throws Exception {
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		Definitions defs = new WSDLParser().parse(ctx);

		List<Validator> validators = new ArrayList<Validator>();
		List<Schema> pSchemas = defs.getTypes().getAllSchemas();
		for (Schema pSchema : pSchemas) {
			javax.xml.validation.Schema schema = sf.newSchema(new StreamSource(new ByteArrayInputStream(pSchema.getAsString().getBytes())));
			Validator validator = schema.newValidator();
			validator.setErrorHandler(handler);
			validators.add(validator);
		}
		return validators;
	}

	public String getWsdl() {
		return wsdl;
	}

	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}

	private static SAXSource getSAXSource(InputStream stream) throws Exception {
		return new SAXSource(new SOAPXMLFilter(XMLReaderFactory.createXMLReader()), new InputSource(stream));
	}
	
}
