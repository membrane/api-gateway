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
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.util.List;

import static com.predic8.membrane.annot.Constants.XSD_NS;

public class XMLSchemaValidator extends AbstractXMLSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidator.class);

    public XMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) {
        super(resourceResolver, location, failureHandler);
    }

    public XMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler, ErrorDetailsPolicy errorDetailsPolicy) {
        super(resourceResolver, location, failureHandler, errorDetailsPolicy);
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
    protected List<Schema> compileSchemas() {
        SchemaFactory sf = HardenedSchemaFactory.newInstance(XSD_NS);
        sf.setResourceResolver(resolver.toLSResourceResolver());
        log.debug("Compiling schema: {}", location);
        StreamSource ss;
        try {
            ss = new StreamSource(resolver.resolve(location));
        } catch (ResourceRetrievalException e) {
            throw new ConfigurationException("Cannot resolve schema from %s.".formatted(location), e);
        }
        ss.setSystemId(location);
        try {
            return List.of(sf.newSchema(ss));
        } catch (SAXException e) {
            throw new ConfigurationException("Cannot parse schema from %s.".formatted(location), e);
        }
    }

    @Override
    protected Validator createValidator(Schema schema) {
        Validator validator = super.createValidator(schema);
        validator.setResourceResolver(resolver.toLSResourceResolver());
        return validator;
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
            XMLReader reader = HardenedSaxParser.newSAXParser().getXMLReader();
            return new SAXSource(reader, new InputSource(input));
        } catch (SAXException e) {
            throw new RuntimeException("Failed to create hardened SAX reader for schema validation", e);
        }
    }

    @Override
    protected void setErrorResponse(Exchange exchange, Interceptor.Flow flow, String message) {
        errorDetailsPolicy.problemDetails(getName(), getErrorTitle(), pd -> pd.topLevel("error", message))
                .buildAndSetResponse(exchange);
    }

    @Override
    protected void setErrorResponse(Exchange exchange, Interceptor.Flow flow, List<Exception> exceptions) {
        errorDetailsPolicy.problemDetails(getName(), getErrorTitle(),
                        pd -> pd.topLevel("validation", convertExceptionsToMap(exceptions)))
                .buildAndSetResponse(exchange);
    }

    @Override
    protected String getPreliminaryError(XOPReconstitutor xopr, Message msg) {
        return null;
    }
}
