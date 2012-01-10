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

import java.security.InvalidParameterException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * Basically switches over {@link WSDLValidator}, {@link XMLSchemaValidator},
 * {@link JSONValidator} and {@link SchematronValidator} depending on the
 * attributes.
 */
public class ValidatorInterceptor extends AbstractInterceptor {
		
	private String wsdl;
	private String schema;
	private String jsonSchema;
	private String schematron;
	
	private IValidator validator;
	
	public ValidatorInterceptor() {
		name =	"Validator";
	}

	private void setValidator(IValidator validator) throws Exception {
		if (validator == null)
			throw new InvalidParameterException("validator is null");
		if (this.validator != null)
			throw new Exception("<validator> cannot have more than one validator attribute.");
		this.validator = validator;
	}
	
	public void init() throws Exception {
		validator = null;
		
		if (wsdl != null)
			setValidator(new WSDLValidator(wsdl));
		if (schema != null)
			setValidator(new XMLSchemaValidator(schema));
		if (jsonSchema != null)
			setValidator((IValidator) Class.forName("com.predic8.membrane.core.interceptor.schemavalidation.JSONValidator").getConstructor(String.class).newInstance(jsonSchema));
		if (schematron != null)
			setValidator(new SchematronValidator(schematron, router.getResourceResolver(), router));
		
		if (validator == null)
			throw new Exception("<validator> must have an attribute specifying the validator.");
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (!exc.getRequest().isPOSTRequest())
			return Outcome.CONTINUE;
			
		return validator.validateMessage(exc, exc.getRequest());
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		// Yes! we want to check if the request is not a POST-request
		if (!exc.getRequest().isPOSTRequest())
			return Outcome.CONTINUE;
		
		return validator.validateMessage(exc, exc.getResponse());
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("validator");
		if (schema != null)
			out.writeAttribute("schema", schema);
		if (wsdl != null)
			out.writeAttribute("wsdl", wsdl);
		if (jsonSchema != null)
			out.writeAttribute("jsonSchema", jsonSchema);
		if (schematron != null)
			out.writeAttribute("schematron", schematron);
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		wsdl = token.getAttributeValue("", "wsdl");
		schema = token.getAttributeValue("", "schema");
		jsonSchema = token.getAttributeValue("", "jsonSchema");
		schematron = token.getAttributeValue("", "schematron");
	}
	
	@Override
	protected void doAfterParsing() throws Exception {
		init();
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

	public String getJsonSchema() {
		return jsonSchema;
	}

	public void setJsonSchema(String jsonSchema) {
		this.jsonSchema = jsonSchema;
	}

	public String getSchematron() {
		return schematron;
	}

	public void setSchematron(String schematron) {
		this.schematron = schematron;
	}
	
}
