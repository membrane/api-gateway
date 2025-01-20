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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.schema.Schema;
import org.slf4j.*;
import org.xml.sax.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;

public class XMLSchemaValidator extends AbstractXMLSchemaValidator {
    private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidator.class.getName());

    public XMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) {
        super(resourceResolver, location, failureHandler);
        init(); // Better to call in Interceptor?
    }

    @Override
    public String getName() {
        return "xml-schema-validator";
    }

    @Override
    protected List<Schema> getSchemas() {
        return null; // never gets called
    }

    @Override
    protected List<Validator> createValidators() {
        SchemaFactory sf = SchemaFactory.newInstance(XSD_NS);
        sf.setResourceResolver(resolver.toLSResourceResolver());
        List<Validator> validators = new ArrayList<>();
        log.debug("Creating validator for schema: {}", location);
        StreamSource ss;
        try {
            ss = new StreamSource(resolver.resolve(location));
        } catch (ResourceRetrievalException e) {
            throw new ConfigurationException("Cannot resolve schema from %s.".formatted(location), e);
        }
        ss.setSystemId(location);
        Validator validator;
        try {
            validator = sf.newSchema(ss).newValidator();
        } catch (SAXException e) {
            throw new ConfigurationException("Cannot parse schema from %s.".formatted(location), e);
        }
        validator.setResourceResolver(resolver.toLSResourceResolver());
        validator.setErrorHandler(new SchemaValidatorErrorHandler());
        validators.add(validator);
        return validators;
    }

    /**
     * Time is dependent on Source type. Messured on Mac M1 and 1_000_000 validations
     * StreamSource = 6.2s
     * DOMSource = 38.8s
     *
     * @param input Stream with body
     * @return Source
     */
    @Override
    protected Source getMessageBody(InputStream input) {
        return new StreamSource(input);
    }

    @Override
    protected void setErrorResponse(Exchange exchange, String message) {
        user(false,getName())
                .title(getErrorTitle())
                .component(getName())
                .internal("error", message)
                .buildAndSetResponse(exchange);
    }

    @Override
    protected void setErrorResponse(Exchange exchange, Interceptor.Flow flow, List<Exception> exceptions) {
        user(false,getName())
                .title(getErrorTitle())
                .internal("validation", convertExceptionsToMap(exceptions))
                .buildAndSetResponse(exchange);
        exchange.getResponse().getHeader().add(VALIDATION_ERROR_SOURCE, flow.name());
    }

    @Override
    protected boolean isFault(Message msg) {
        return false;
    }

    @Override
    protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
        return null;
    }
}
