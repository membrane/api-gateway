/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ResourceResolver;
import com.predic8.membrane.core.util.TextUtil;

/**
 * Basically switches over {@link WSDLValidator}, {@link XMLSchemaValidator},
 * {@link JSONValidator} and {@link SchematronValidator} depending on the
 * attributes.
 */
public class ValidatorInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(ValidatorInterceptor.class.getName());

	private String wsdl;
	private String schema;
	private String jsonSchema;
	private String schematron;
	private String failureHandler;
	private boolean skipFaults;
	
	private IValidator validator;
	private ResourceResolver resourceResolver;
	
	private void setValidator(IValidator validator) throws Exception {
		if (this.validator != null)
			throw new Exception("<validator> cannot have more than one validator attribute.");
		this.validator = validator;
	}
	
	public void init() throws Exception {
		validator = null;
			
		if (wsdl != null) {
			name="SOAP Validator";
			setValidator(new WSDLValidator(resourceResolver, wsdl, createFailureHandler(), skipFaults));
		}
		if (schema != null) {
			name="XML Schema Validator";
			setValidator(new XMLSchemaValidator(resourceResolver, schema, createFailureHandler()));
		}
		if (jsonSchema != null) {
			name="JSON Schema Validator";
			setValidator(new JSONValidator(resourceResolver, jsonSchema, createFailureHandler()));
		}
		if (schematron != null) {
			name="Schematron Validator";
			setValidator(new SchematronValidator(resourceResolver, schematron, createFailureHandler(), router));
		}
		
		if (validator == null)
			throw new Exception("<validator> must have an attribute specifying the validator.");
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().isBodyEmpty()) 
			return Outcome.CONTINUE;
			
		return validator.validateMessage(exc, exc.getRequest());
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		if (exc.getResponse().isBodyEmpty())
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
		if (failureHandler != null)
			out.writeAttribute("failureHandler", failureHandler);
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		wsdl = token.getAttributeValue("", "wsdl");
		schema = token.getAttributeValue("", "schema");
		jsonSchema = token.getAttributeValue("", "jsonSchema");
		schematron = token.getAttributeValue("", "schematron");
		failureHandler = token.getAttributeValue("", "failureHandler");
	}
	
	@Override
	public void doAfterParsing() throws Exception {
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
	
	public String getFailureHandler() {
		return failureHandler;
	}
	
	public void setFailureHandler(String failureHandler) {
		this.failureHandler = failureHandler;
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
	
	public boolean isSkipFaults() {
		return skipFaults;
	}
	
	public void setSkipFaults(boolean skipFaults) {
		this.skipFaults = skipFaults;
	}
	
	@Override
	public void setRouter(Router router) {
		super.setRouter(router);
		resourceResolver = router.getResourceResolver();
	}
	
	public void setResourceResolver(ResourceResolver resourceResolver) {
		this.resourceResolver = resourceResolver;
	}
	
	@Override
	public String getShortDescription() {
		return validator.getInvalid() + " of " + (validator.getValid() + validator.getInvalid()) + " messages have been invalid.";
	}
	
	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder(getShortDescription());
		sb.deleteCharAt(sb.length()-1);
		sb.append(" according to ");
		if (wsdl != null) {
			sb.append("the WSDL at <br/>");
			sb.append(TextUtil.linkURL(wsdl));
		}
		if (schema != null) {
			sb.append("the XML Schema at <br/>");
			sb.append(TextUtil.linkURL(schema));
		}
		if (jsonSchema != null) {
			sb.append("the JSON Schema at <br/>");
			sb.append(TextUtil.linkURL(jsonSchema));
		}
		if (schematron != null) {
			sb.append("the Schematron at <br/>");
			sb.append(TextUtil.linkURL(schematron));
		}
		sb.append(" .");
		return sb.toString();
	}
	
	public static interface FailureHandler {
		public static final FailureHandler VOID = new FailureHandler(){
			@Override
			public void handleFailure(String message, Exchange exc) {
			}};
		
		void handleFailure(String message, Exchange exc);
	}
	
	private FailureHandler createFailureHandler() {
		if (failureHandler == null || failureHandler.equals("response"))
			return null;
		if (failureHandler.equals("log"))
			return new FailureHandler() {
				@Override
				public void handleFailure(String message, Exchange exc) {
					log.info("Validation failure: " + message);
				}
			};
		throw new IllegalArgumentException("Unknown failureHandler type: " + failureHandler);
	}
	
	@Override
	public String getHelpId() {
		return "validator";
	}

}
