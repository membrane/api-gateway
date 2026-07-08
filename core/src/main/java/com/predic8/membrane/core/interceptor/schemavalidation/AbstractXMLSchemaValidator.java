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
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static com.predic8.membrane.annot.Constants.XSD_NS;
import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

public abstract class AbstractXMLSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(AbstractXMLSchemaValidator.class.getName());
    protected final XOPReconstitutor xopr;
    protected final String location;
    protected final ResolverMap resolver;
    protected final ValidatorInterceptor.FailureHandler failureHandler;
    protected final AtomicLong valid = new AtomicLong();
    protected final AtomicLong invalid = new AtomicLong();
    private ArrayBlockingQueue<List<Validator>> validators;

    public AbstractXMLSchemaValidator(ResolverMap resolver, String location, ValidatorInterceptor.FailureHandler failureHandler) {
        this.location = location;
        this.resolver = resolver;
        this.failureHandler = failureHandler;
        xopr = new XOPReconstitutor();
    }

    private static @NotNull Map<String, Object> createErrorEntry(Exception e) {
        var error = new LinkedHashMap<String, Object>();
        error.put("message", e.getMessage());
        if (e instanceof SAXParseException spe) {
            error.put("line", spe.getLineNumber());
            error.put("column", spe.getColumnNumber());
        }
        return error;
    }

    public void init() {
        super.init();
        int concurrency = Runtime.getRuntime().availableProcessors() * 2;
        validators = new ArrayBlockingQueue<>(concurrency);
        for (int i = 0; i < concurrency; i++)
            validators.add(createValidators());
    }

    public Outcome validateMessage(Exchange exc, Interceptor.Flow flow) throws Exception {
        var msg = exc.getMessage(flow);
        var exceptions = new ArrayList<Exception>();
        var preliminaryError = getPreliminaryError(xopr, msg);
        if (preliminaryError == null) {
            var vals = validators.take();
            try {
                // the message must be valid for one schema embedded into WSDL
                for (var validator : vals) {
                    var handler = (SchemaValidatorErrorHandler) validator.getErrorHandler();
                    try {
                        validator.validate(getMessageBody(xopr.reconstituteIfNecessary(msg)));
                        if (handler.noErrors()) {
                            valid.incrementAndGet();
                            return CONTINUE;
                        }
                        exceptions.add(handler.getException());
                    } finally {
                        handler.reset();
                    }
                }
            } catch (Exception e) {
                exceptions.add(e);
            } finally {
                validators.put(vals);
            }
        } else {
            exceptions.add(new Exception(preliminaryError));
        }
        // Reached only when the message matched none of the (possibly several) embedded schemas.
        var errorMsg = getErrorMsg(exceptions); // Errors als simple String
        log.info("{} message did not validate against any schema of {}: {}", flow, location, errorMsg);
        if (failureHandler != null) {
            failureHandler.handleFailure(errorMsg, exc);
        }
        exc.setProperty("error", errorMsg); // TODO Search for usage. If it is used rename property. See properties in class Exchange
        setErrorResponse(exc, flow, exceptions);
        exc.getResponse().getHeader().add(VALIDATION_ERROR_SOURCE, flow.name());
        invalid.incrementAndGet();
        return ABORT;
    }

    protected List<Validator> createValidators() {
        var sf = SchemaFactory.newInstance(XSD_NS);
        sf.setResourceResolver(getResourceResolver());
        var validators = new ArrayList<Validator>();
        var schemas = getSchemas();
        for (int i = 0; i < schemas.size(); i++) {
            var schema = schemas.get(i);
            log.debug("Creating validator {}/{} for schema at: {}", i + 1, schemas.size(), location);
            validators.add(createValidator(schema, sf));
        }
        return validators;
    }

    /**
     * Resolver used while compiling the schemas. Subclasses may override to resolve references
     * that the default location-based {@link ResolverMap} resolver cannot handle, e.g. a
     * namespace-only {@code <xsd:import>} between two schemas embedded in the same WSDL.
     */
    protected LSResourceResolver getResourceResolver() {
        return resolver.toLSResourceResolver();
    }

    private @NotNull Validator createValidator(Element schema, SchemaFactory sf) {
        try {
            var source = new DOMSource(schema);
            source.setSystemId(location);
            var validator = sf.newSchema(source).newValidator();
            validator.setErrorHandler(new SchemaValidatorErrorHandler());
            return validator;
        } catch (SAXException e) {
            throw new ConfigurationException("Cannot read schema %s".formatted(location), e);
        }
    }

    private String getErrorMsg(List<Exception> excs) {
        var buf = new StringBuilder();
        buf.append("%s: ".formatted(getErrorTitle()));
        for (var e : excs) {
            buf.append(e);
            buf.append("; ");
        }
        return buf.toString();
    }

    protected List<Map<String, Object>> convertExceptionsToMap(List<Exception> exceptions) {
        return exceptions.stream().map(AbstractXMLSchemaValidator::createErrorEntry).toList();
    }

    @Override
    public long getValid() {
        return valid.get();
    }

    @Override
    public long getInvalid() {
        return invalid.get();
    }

    protected abstract List<Element> getSchemas();

    protected abstract Source getMessageBody(InputStream input);

    protected abstract void setErrorResponse(Exchange exchange, String message);

    protected abstract void setErrorResponse(Exchange exchange, Interceptor.Flow flow, List<Exception> exceptions);

    protected abstract String getPreliminaryError(XOPReconstitutor xopr, Message msg);

    @Override
    public String getErrorTitle() {
        return "XML message validation failed";
    }
}
