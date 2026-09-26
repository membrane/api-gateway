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
import com.predic8.membrane.core.util.xml.parser.HardenedSchemaFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

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
    protected final ErrorDetailsPolicy errorDetailsPolicy;
    protected final AtomicLong valid = new AtomicLong();
    protected final AtomicLong invalid = new AtomicLong();
    private ValidatorPool<List<Validator>> validators;

    /**
     * The embedded schemas, compiled once. A {@link Schema} is thread-safe, so new pool entries
     * only need a cheap {@link Schema#newValidator()}. Set by the first {@link #createValidators()}
     * call, which comes from {@link #init()} filling the pool, before any request thread runs.
     */
    private List<Schema> compiledSchemas;

    public AbstractXMLSchemaValidator(ResolverMap resolver, String location, ValidatorInterceptor.FailureHandler failureHandler) {
        this(resolver, location, failureHandler, ErrorDetailsPolicy.FULL);
    }

    public AbstractXMLSchemaValidator(ResolverMap resolver, String location, ValidatorInterceptor.FailureHandler failureHandler, ErrorDetailsPolicy errorDetailsPolicy) {
        this.location = location;
        this.resolver = resolver;
        this.failureHandler = failureHandler;
        this.errorDetailsPolicy = errorDetailsPolicy;
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
        validators = new ValidatorPool<>(this::createValidators, poolConcurrency());
    }

    /**
     * Number of validators a pool creates up front; it grows beyond that on demand. Exposed so subclasses that keep an additional pool of their own
     * (e.g. {@link WSDLValidator}'s SOAP fault structure validators) size it identically.
     */
    protected static int poolConcurrency() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    public Outcome validateMessage(Exchange exc, Interceptor.Flow flow) throws Exception {
        var msg = exc.getMessage(flow);
        var exceptions = new ArrayList<Exception>();
        var preliminaryError = getPreliminaryError(xopr, msg);
        boolean isValid = false;
        if (preliminaryError == null) {
            isValid = validateAgainstSchemas(() -> getMessageBody(xopr.reconstituteIfNecessary(msg)), exceptions);
        } else {
            exceptions.add(new Exception(preliminaryError));
        }
        if (isValid) {
            valid.incrementAndGet();
            return CONTINUE;
        }
        // Reached only when the message matched none of the (possibly several) embedded schemas.
        return abort(exc, flow, exceptions);
    }

    /**
     * Rejects a message that matched none of the schemas, reporting the errors collected per
     * schema.
     */
    protected Outcome abort(Exchange exc, Interceptor.Flow flow, List<Exception> exceptions) {
        var errorMsg = getErrorMsg(exceptions); // Errors als simple String
        setErrorResponse(exc, flow, exceptions);
        return finishAbort(exc, flow, "message did not validate against any schema of %s: %s".formatted(location, errorMsg), errorMsg);
    }

    /**
     * Rejects a message for a reason other than schema validation - because it is not SOAP at all,
     * say, or carries an element the WSDL does not describe.
     */
    protected Outcome abort(Exchange exc, Interceptor.Flow flow, String message) {
        setErrorResponse(exc, flow, message);
        return finishAbort(exc, flow, "message rejected: " + message, message);
    }

    /**
     * The steps every rejection takes, whatever was wrong with the message: log it, notify the
     * configured failure handler, expose the reason on the exchange, mark the response with the
     * flow the message was rejected in and count it as invalid. Rejections must go through
     * {@link #abort} so that none of this is left out.
     */
    private Outcome finishAbort(Exchange exc, Interceptor.Flow flow, String logMessage, String errorMsg) {
        log.info("{} {}", flow, logMessage);
        if (failureHandler != null) {
            failureHandler.handleFailure(errorMsg, exc);
        }
        exc.setProperty("error", errorMsg); // TODO Search for usage. If it is used rename property. See properties in class Exchange
        exc.getResponse().getHeader().add(VALIDATION_ERROR_SOURCE, flow.name());
        invalid.incrementAndGet();
        return ABORT;
    }

    /**
     * Validates a {@link Source} against every schema embedded in the WSDL, succeeding if it
     * matches any one of them. Exposed as {@code protected} so subclasses can run schema
     * validation against a narrower source than the whole message body (e.g. {@link WSDLValidator}
     * validates a SOAP fault's {@code detail} content this way).
     * <p>
     * Takes a {@link Supplier} rather than a single {@link Source} because a {@code Source}
     * backed by a stream is consumed after one {@code validate()} call - a fresh one is needed
     * for each of the (possibly several) embedded schemas tried.
     * <p>
     * A schema whose validator run itself throws (as opposed to reporting validation errors
     * through its {@link SchemaValidatorErrorHandler}) is logged immediately - unlike an ordinary
     * validation mismatch, it signals something more serious (e.g. a schema failing to resolve an
     * import) that must not go unnoticed just because another embedded schema goes on to match -
     * and the remaining schemas are still tried; one broken schema must not stop a message from
     * matching another one of the (possibly several) alternatives.
     *
     * @param exceptions collects the errors reported for each schema the source failed against
     * @return {@code true} if the source matched at least one embedded schema
     */
    protected boolean validateAgainstSchemas(Supplier<Source> source, List<Exception> exceptions) {
        var vals = validators.borrow();
        try {
            // the message must be valid for one schema embedded into WSDL
            for (var validator : vals) {
                try {
                    if (validateOnce(validator, source.get(), exceptions)) {
                        return true;
                    }
                } catch (Exception e) {
                    logValidatorFailure(e);
                    if (!exceptions.contains(e)) {
                        exceptions.add(e);
                    }
                }
            }
        } finally {
            validators.release(vals);
        }
        return false;
    }

    /**
     * Validates a {@link Source} against the single schema a borrowed validator was compiled for.
     * For pools whose validators are not interchangeable alternatives but are each the one right
     * validator for a given kind of message - e.g. {@link WSDLValidator}'s SOAP fault structure
     * schemas, one per SOAP version.
     *
     * @param exceptions collects the validation error if the source did not validate
     * @return {@code true} if the source validated
     */
    protected boolean validateWith(ValidatorPool<Validator> pool, Source source, List<Exception> exceptions) {
        var validator = pool.borrow();
        try {
            return validateOnce(validator, source, exceptions);
        } catch (Exception e) {
            logValidatorFailure(e);
            if (!exceptions.contains(e)) {
                exceptions.add(e);
            }
            return false;
        } finally {
            pool.release(validator);
        }
    }

    /**
     * A validator throwing (rather than reporting an ordinary validation error through its
     * {@link SchemaValidatorErrorHandler}) signals something more serious than "the message
     * doesn't match this schema" - e.g. a schema failing to resolve an import. Unlike ordinary
     * validation errors, which are only logged once none of the (possibly several) embedded
     * schemas matched, this must be logged unconditionally so it isn't lost.
     */
    private void logValidatorFailure(Exception e) {
        log.warn("Validator for {} threw while validating: {}", location, e.getMessage(), e);
    }

    /**
     * Runs one validator over one source and resets its error handler afterwards, so that the
     * validator can be returned to its pool ready for the next use.
     */
    private boolean validateOnce(Validator validator, Source source, List<Exception> exceptions) throws IOException, SAXException {
        var handler = (SchemaValidatorErrorHandler) validator.getErrorHandler();
        try {
            validator.validate(source);
            if (handler.noErrors()) {
                return true;
            }
            exceptions.addAll(handler.getExceptions());
            return false;
        } catch (IOException | SAXException e) {
            var reported = handler.getExceptions();
            exceptions.addAll(reported);
            if (!reported.contains(e)) {
                exceptions.add(e);
            }
            throw e;
        } finally {
            handler.reset();
        }
    }

    /**
     * Creates one validator per schema, forming one pool entry. Called from {@link #init()} and,
     * when the pool is exhausted, from request threads.
     */
    protected List<Validator> createValidators() {
        if (compiledSchemas == null)
            compiledSchemas = compileSchemas();
        var validators = new ArrayList<Validator>(compiledSchemas.size());
        for (var schema : compiledSchemas)
            validators.add(createValidator(schema));
        return validators;
    }

    /**
     * Compiles the schemas validated against. Runs once, during {@link #init()}.
     */
    protected List<Schema> compileSchemas() {
        var sf = HardenedSchemaFactory.newInstance(XSD_NS);
        sf.setResourceResolver(getResourceResolver());
        var compiled = new ArrayList<Schema>();
        var schemas = getSchemas();
        for (int i = 0; i < schemas.size(); i++) {
            log.debug("Compiling schema {}/{} at: {}", i + 1, schemas.size(), location);
            compiled.add(compileSchema(schemas.get(i), sf));
        }
        return List.copyOf(compiled);
    }

    /**
     * Creates a ready-to-use, hardened validator for a compiled schema.
     */
    protected Validator createValidator(Schema schema) {
        var validator = schema.newValidator();
        validator.setErrorHandler(new SchemaValidatorErrorHandler());
        HardenedSchemaFactory.hardenValidator(validator);
        return validator;
    }

    /**
     * Resolver used while compiling the schemas. Subclasses may override to resolve references
     * that the default location-based {@link ResolverMap} resolver cannot handle, e.g. a
     * namespace-only {@code <xsd:import>} between two schemas embedded in the same WSDL.
     */
    protected LSResourceResolver getResourceResolver() {
        return resolver.toLSResourceResolver();
    }

    private @NotNull Schema compileSchema(Element schema, SchemaFactory sf) {
        try {
            var source = new DOMSource(schema);
            source.setSystemId(location);
            return sf.newSchema(source);
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

    protected abstract void setErrorResponse(Exchange exchange, Interceptor.Flow flow, String message);

    protected abstract void setErrorResponse(Exchange exchange, Interceptor.Flow flow, List<Exception> exceptions);

    protected abstract String getPreliminaryError(XOPReconstitutor xopr, Message msg);

    @Override
    public String getErrorTitle() {
        return "XML message validation failed";
    }
}
