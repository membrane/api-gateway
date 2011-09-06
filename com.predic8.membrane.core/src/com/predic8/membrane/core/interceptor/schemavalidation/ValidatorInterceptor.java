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

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLReaderFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.schema.*;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class ValidatorInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(ValidatorInterceptor.class.getName());
		
	private List<Validator> validators;
	
	private String wsdl;
	private String schema;
	
	public ValidatorInterceptor() {
		name =	"Validator";
	}
	
	public void init() throws Exception {
		validators = getValidators();
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (!exc.getRequest().isPOSTRequest())
			return Outcome.CONTINUE;
			
		return validateMessage(exc, exc.getRequest());
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		// Yes! we want to check if the request is not a POST-request
		if (!exc.getRequest().isPOSTRequest())
			return Outcome.CONTINUE;
		
		return validateMessage(exc, exc.getResponse());
	}
	
	private Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		List<Exception> exceptions = new ArrayList<Exception>();
		for (Validator validator: validators) {
			validator.validate(getMessageBody(msg.getBodyAsStream()));
			SchemaValidatorErrorHandler handler = (SchemaValidatorErrorHandler)validator.getErrorHandler();
			// the message must be valid for one schema embedded into WSDL 
			if (handler.noErrors()) {
				return Outcome.CONTINUE;
			}
			exceptions.add(handler.getException());
			handler.reset();
		}
		exc.setResponse(HttpUtil.createSOAPFaultResponse(getErrorMsg(exceptions)));
		return Outcome.ABORT;
	}

	private Source getMessageBody(InputStream input) throws Exception {
		if ( schema != null ) {
			return new SAXSource(new InputSource(input));
		}
		return getSOAPBody(input);
	}
	
	private String getErrorMsg(List<Exception> excs) {
		StringBuffer buf = new StringBuffer();
		buf.append("Validation failed: ");
		for (Exception e : excs) {
			buf.append(e);
			buf.append("; ");
		}
		return buf.toString();
	}
	
	private List<Validator> getValidators() throws Exception {
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		SOAModelResourceResolver resolver = getResolver();
		sf.setResourceResolver(resolver);
		
		List<Validator> validators = new ArrayList<Validator>();
		for (Schema schema : resolver.schemas) {
			log.info("Creating validator for schema: " + schema);
			Validator validator = sf.newSchema(new StreamSource(new StringReader(schema.getAsString()))).newValidator();
			validator.setErrorHandler(new SchemaValidatorErrorHandler());
			validators.add(validator);
		}
		return validators;
	}
	
	private SOAModelResourceResolver getResolver() {
		SOAModelResourceResolver resolver = new SOAModelResourceResolver();
		if (schema != null) {
			resolver.loadFromSchema(schema);
		} else {
			resolver.loadFromWSDL(wsdl);
		}
		return resolver;
	}
	
	private static Source getSOAPBody(InputStream stream) throws Exception {
		return new SAXSource(new SOAPXMLFilter(XMLReaderFactory.createXMLReader()), new InputSource(stream));
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}
	
	public String getWsdl() {
		return wsdl;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("validator");
		
		if (schema != null) {
			out.writeAttribute("schema", schema);
		}
		if (wsdl != null) {
			out.writeAttribute("wsdl", wsdl);
		}
				
		
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		
		wsdl = token.getAttributeValue("", "wsdl");
		schema = token.getAttributeValue("", "schema");
	}
	
	@Override
	protected void doAfterParsing() throws Exception {
		init();
	}
	
}
