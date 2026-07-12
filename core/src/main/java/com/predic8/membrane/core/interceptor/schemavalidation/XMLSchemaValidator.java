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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.xml.parser.HardenedSaxParser;
import com.predic8.membrane.core.util.xml.parser.HardenedSchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.Constants.XSD_NS;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;

public class XMLSchemaValidator extends AbstractXMLSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidator.class);

    public XMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) {
        super(resourceResolver, location, failureHandler);
        init(); // Better to call in Interceptor?
    }

    @Override
    public String getName() {
        return "xml-schema-validator";
    }

    @Override
    protected List<Element> getSchemas() {
        return null; // never gets called
    }

    @Override
    protected List<Validator> createValidators() {
        SchemaFactory sf = HardenedSchemaFactory.newInstance(XSD_NS);
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
        HardenedSchemaFactory.hardenValidator(validator);
        validators.add(validator);
        return validators;
    }

    /**
     * Returns a {@link SAXSource} backed by {@link HardenedSaxParser} so the Validator
     * cannot fetch external DTDs from instance documents regardless of Validator property support.
     *
     * @param input Stream with body
     * @return Source
     */
    @Override
    protected Source getMessageBody(InputStream input) {
        try {
            XMLReader reader = HardenedSaxParser.getInstance().newSAXParser().getXMLReader();
            return new SAXSource(reader, new InputSource(input));
        } catch (SAXException e) {
            throw new RuntimeException("Failed to create hardened SAX reader for schema validation", e);
        }
    }

    @Override
    protected void setErrorResponse(Exchange exchange, String message) {
        user(false,getName())
                .title(getErrorTitle())
                .addSubType("validation")
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
    protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
        return null;
    }
}
