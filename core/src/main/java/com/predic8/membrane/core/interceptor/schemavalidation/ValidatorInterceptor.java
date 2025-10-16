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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.json.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.beans.*;
import org.springframework.context.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.resolver.ResolverMap.*;
import static com.predic8.membrane.core.util.TextUtil.linkURL;

/**
 * Basically switches over {@link WSDLValidator}, {@link XMLSchemaValidator},
 * {@link JSONSchemaValidator} and {@link SchematronValidator} depending on the
 * attributes.
 *
 * @topic 3. Security and Validation
 */
@MCElement(name = "validator")
public class ValidatorInterceptor extends AbstractInterceptor implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ValidatorInterceptor.class.getName());

    private String wsdl;
    private String schema;
    private String serviceName;
    private String jsonSchema;

    /**
     * Schema version e.g. JSON Schema version 04, 07, 2020-12
     * Could also be used for XML or WSDL schema versions later.
     */
    private String schemaVersion = "2020-12";

    private String schematron;
    private String failureHandler;
    private boolean skipFaults;

    private MessageValidator validator;
    private ResolverMap resourceResolver;
    private ApplicationContext applicationContext;

    public ValidatorInterceptor() {
        name = "validator";
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void init() {
        super.init();
        resourceResolver = router.getResolverMap();
        try {
            validator = getMessageValidator();
            validator.init();
        } catch (Exception e) {
            throw new ConfigurationException("Cannot create message validator.", e);
        }
        name = validator.getName();
        if (skipFaults && wsdl == null)
            throw new ConfigurationException("validator/@skipFaults only makes sense with validator/@wsdl.");
    }

    private MessageValidator getMessageValidator() throws Exception {
        if (wsdl != null) {
            return new WSDLValidator(resourceResolver, combine(getBaseLocation(), wsdl), serviceName, createFailureHandler(), skipFaults);
        }
        if (schema != null) {
            return new XMLSchemaValidator(resourceResolver, combine(getBaseLocation(), schema), createFailureHandler());
        }
        if (jsonSchema != null) {
            return new JSONYAMLSchemaValidator(resourceResolver, combine(getBaseLocation(), jsonSchema), createFailureHandler(), schemaVersion);
        }
        if (schematron != null) {
            return new SchematronValidator(combine(getBaseLocation(), schematron), createFailureHandler(), router, applicationContext);
        }

        WSDLValidator validator = getWsdlValidatorFromSOAPProxy();
        if (validator != null) return validator;

        throw new RuntimeException("Validator is not configured properly. <validator> must have an attribute specifying the validator.");
    }

    private @Nullable WSDLValidator getWsdlValidatorFromSOAPProxy() {
        if (router.getParentProxy(this) instanceof SOAPProxy sp) {
            wsdl = sp.getWsdl();
            name = "soap validator";
            return new WSDLValidator(resourceResolver, combine(getBaseLocation(), wsdl), serviceName, createFailureHandler(), skipFaults);
        }
        return null;
    }

    private @Nullable String getBaseLocation() {
        return router == null ? null : router.getBaseLocation();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        try {
            if (exc.getMessage(flow).isBodyEmpty())
                return CONTINUE;
        } catch (IOException e) {
            log.error("", e);
            internal(router.isProduction(),getDisplayName())
                    .addSubSee("io")
                    .detail("Could not read message body")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        try {
            return validator.validateMessage(exc, flow);
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(),getDisplayName())
                    .detail("Error validating message")
                    .addSubSee("generic")
                    .internal("validator", validator.getName())
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
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
     * be generic and the validation error will be logged.
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

    public String getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * @description The version of the Schema.
     * @example 04, 05, 06, 07, 2019-09, 2020-12
     * @default 2020-12
     */
    @MCAttribute
    public void setSchemaVersion(String version) {
        this.schemaVersion = version;
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
        sb.deleteCharAt(sb.length() - 1);
        sb.append(" according to ");
        if (wsdl != null) {
            sb.append("the WSDL at <br/>");
            sb.append(linkURL(wsdl));
        }
        if (schema != null) {
            sb.append("the XML Schema at <br/>");
            sb.append(linkURL(schema));
        }
        if (jsonSchema != null) {
            sb.append("the JSON Schema at <br/>");
            sb.append(linkURL(jsonSchema));
        }
        if (schematron != null) {
            sb.append("the Schematron at <br/>");
            sb.append(linkURL(schematron));
        }
        sb.append(" .");
        return sb.toString();
    }

    public interface FailureHandler {
        FailureHandler VOID = (message, exc) -> {
        };

        void handleFailure(String message, Exchange exc);
    }

    private FailureHandler createFailureHandler() {
        if (failureHandler == null || failureHandler.equals("response"))
            return (msg,exchange) -> {};
        if (failureHandler.equals("log"))
            return (message, exc) -> log.info("Validation failure: {}", message);
        throw new IllegalArgumentException("Unknown failureHandler type: " + failureHandler);
    }

}
