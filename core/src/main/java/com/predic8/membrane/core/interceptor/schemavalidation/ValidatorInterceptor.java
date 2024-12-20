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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.SOAPProxy;
import com.predic8.membrane.core.util.TextUtil;
import org.jetbrains.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;

/**
 * Basically switches over {@link WSDLValidator}, {@link XMLSchemaValidator},
 * {@link JSONValidator} and {@link SchematronValidator} depending on the
 * attributes.
 * @topic 8. SOAP based Web Services
 */
@MCElement(name="validator")
public class ValidatorInterceptor extends AbstractInterceptor implements ApplicationContextAware {

	private static final Logger log = LoggerFactory.getLogger(ValidatorInterceptor.class.getName());

	private String wsdl;
	private String schema;
	private String serviceName;
	private String jsonSchema;
	private String schematron;
	private String failureHandler;
	private boolean skipFaults;

	private MessageValidator validator;
	private ResolverMap resourceResolver;
	private ApplicationContext applicationContext;

	public ValidatorInterceptor() {
		name = "Validator";
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void init() throws Exception {
		validator = getMessageValidator();
		validator.init();
		name = validator.getName();
		if (skipFaults && wsdl == null)
			throw new Exception("validator/@skipFaults only makes sense with validator/@wsdl");
	}

	private MessageValidator getMessageValidator() throws Exception {
		if (wsdl != null) {
			return new WSDLValidator(resourceResolver, combine(getBaseLocation(), wsdl), serviceName, createFailureHandler(), skipFaults);
		}
		if (schema != null) {
			return new XMLSchemaValidator(resourceResolver, combine(getBaseLocation(), schema), createFailureHandler());
		}
		if (jsonSchema != null) {
			return new JSONValidator(resourceResolver, combine(getBaseLocation(), jsonSchema), createFailureHandler());
		}
		if (schematron != null) {
			return new SchematronValidator( combine(getBaseLocation(), schematron), createFailureHandler(), router, applicationContext);
		}

		if (validator == null) {
			Rule parent = router.getParentProxy(this);
			if (parent instanceof SOAPProxy sp) {
				wsdl = sp.getWsdl();
				name = "SOAP Validator";
				return new WSDLValidator(resourceResolver, combine(getBaseLocation(), wsdl),  serviceName, createFailureHandler(), skipFaults);
			}
		}
		throw new RuntimeException("Validator is not configured properly. <validator> must have an attribute specifying the validator.");
	}

	private @Nullable String getBaseLocation() {
		return router == null ? null : router.getBaseLocation();
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().isBodyEmpty())
			return CONTINUE;

		return validator.validateMessage(exc, exc.getRequest());
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		if (exc.getResponse().isBodyEmpty())
			return CONTINUE;

		return validator.validateMessage(exc, exc.getResponse());
	}

	/**
     * @description The WSDL (URL or file) to validate against.
     * @example <a href="http://predic8.com:8080/material/ArticleService?wsdl">"http://predic8.com:8080/material/ArticleService?wsdl</a>
     */
	@MCAttribute
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}

	public String getWsdl() {
		return wsdl;
	}

	public String getSchema() {
		return schema;
	}

	/**
     * @description The XSD Schema (URL or file) to validate against.
     * @example <a href="http://www.predic8.com/schemas/order.xsd">http://www.predic8.com/schemas/order.xsd</a>
     */
	@MCAttribute
	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getFailureHandler() {
		return failureHandler;
	}

	/**
	 * @description If "response", the HTTP response will include a detailled error message. If "log", the response will
	 *              be generic and the validation error will be logged.
	 * @default response
	 * @example log
	 */
	@MCAttribute
	public void setFailureHandler(String failureHandler) {
		this.failureHandler = failureHandler;
	}

	public String getJsonSchema() {
		return jsonSchema;
	}

	/**
	 * @description The JSON Schema (URL or file) to validate against.
	 * @example examples/validation/json-schema/schema2000.json
	 */
	@MCAttribute
	public void setJsonSchema(String jsonSchema) {
		this.jsonSchema = jsonSchema;
	}

	public String getSchematron() {
		return schematron;
	}

	/**
	 * @description The Schematron schema (URL or file) to validate against.
	 * @example examples/validation/schematron/car-schematron.xml
	 */
	@MCAttribute
	public void setSchematron(String schematron) {
		this.schematron = schematron;
	}

	public boolean isSkipFaults() {
		return skipFaults;
	}

	/**
	 * @description Whether to skip validation for SOAP fault messages.
	 * @default false
	 */
	@MCAttribute
	public void setSkipFaults(boolean skipFaults) {
		this.skipFaults = skipFaults;
	}


	public String getServiceName() {
		return serviceName;
	}

	/**
	 * @description Optional name of a serivce element in a WSDL. If specified it will be
	 * checked if the SOAP element is possible for that service.
	 */
	@MCAttribute
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	@Override
	public void init(Router router) throws Exception {
		resourceResolver = router.getResolverMap();
		super.init(router);
	}

	public void setResourceResolver(ResolverMap resourceResolver) {
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

	public interface FailureHandler {
		FailureHandler VOID = (message, exc) -> {};

		void handleFailure(String message, Exchange exc);
	}

	private FailureHandler createFailureHandler() {
		if (failureHandler == null || failureHandler.equals("response"))
			return null;
		if (failureHandler.equals("log"))
			return (message, exc) -> log.info("Validation failure: " + message);
		throw new IllegalArgumentException("Unknown failureHandler type: " + failureHandler);
	}

}
