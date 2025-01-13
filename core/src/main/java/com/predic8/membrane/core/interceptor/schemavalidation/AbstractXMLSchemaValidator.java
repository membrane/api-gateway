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
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import com.predic8.schema.Schema;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.xml.sax.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public abstract class AbstractXMLSchemaValidator extends AbstractMessageValidator {

	private static final Logger log = LoggerFactory.getLogger(AbstractXMLSchemaValidator.class.getName());

	private ArrayBlockingQueue<List<Validator>> validators;
	protected final XOPReconstitutor xopr;
	protected final String location;
	protected final ResolverMap resolver;
	protected final ValidatorInterceptor.FailureHandler failureHandler;
	private final boolean skipFaults;

	protected final AtomicLong valid = new AtomicLong();
	protected final AtomicLong invalid = new AtomicLong();

	public AbstractXMLSchemaValidator(ResolverMap resolver, String location, ValidatorInterceptor.FailureHandler failureHandler) {
		this(resolver, location, failureHandler, false);
	}

	public AbstractXMLSchemaValidator(ResolverMap resolver, String location, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) {
		this.location = location;
		this.resolver = resolver;
		this.failureHandler = failureHandler;
		this.skipFaults = skipFaults;
		xopr = new XOPReconstitutor();
	}

	public void init() {
		super.init();
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		validators = new ArrayBlockingQueue<>(concurrency);
		for (int i = 0; i < concurrency; i++)
			validators.add(createValidators());
	}

	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		List<Exception> exceptions = new ArrayList<>();
		String preliminaryError = getPreliminaryError(xopr, msg);
		if (preliminaryError == null) {
			List<Validator> vals = validators.take();
			try {
				// the message must be valid for one schema embedded into WSDL
				for (Validator validator: vals) {
					SchemaValidatorErrorHandler handler = (SchemaValidatorErrorHandler)validator.getErrorHandler();
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
		if (skipFaults && isFault(msg)) {
			valid.incrementAndGet();
			return CONTINUE;
		}
		if (failureHandler == FailureHandler.VOID) {
			exc.setProperty("error", getErrorMsg(exceptions));
		} else if (failureHandler != null) {
			failureHandler.handleFailure(getErrorMsg(exceptions), exc);
			exc.setResponse(createErrorResponse("validation error"));
		} else {
			exc.setResponse(createErrorResponse(getErrorMsg(exceptions)));
			exc.getResponse().getHeader().add(VALIDATION_ERROR_SOURCE, getSourceOfError(msg));
		}
		invalid.incrementAndGet();
		return ABORT;
	}

	protected List<Validator> createValidators() {
		SchemaFactory sf = SchemaFactory.newInstance(XSD_NS);
		sf.setResourceResolver(resolver.toLSResourceResolver());
		List<Validator> validators = new ArrayList<>();
		for (Schema schema : getSchemas()) {
			log.debug("Creating validator for schema: {}", schema);
            validators.add(getValidator(schema, sf));
		}
		return validators;
	}

	private @NotNull Validator getValidator(Schema schema, SchemaFactory sf) {
        try {
			Validator validator = sf.newSchema(getStreamSource(schema)).newValidator();
			validator.setResourceResolver(resolver.toLSResourceResolver());
			validator.setErrorHandler(new SchemaValidatorErrorHandler());
			return validator;
        } catch (SAXException e) {
            throw new ConfigurationException("Cannot read schema %s.".formatted(schema.getName()),e);
        }
	}

	private @NotNull StreamSource getStreamSource(Schema schema) {
		StreamSource ss = new StreamSource(new StringReader(schema.getAsString()));
		ss.setSystemId(location);
		return ss;
	}

	private String getErrorMsg(List<Exception> excs) {
		StringBuilder buf = new StringBuilder();
		buf.append("Validation failed: ");
		for (Exception e : excs) {
			buf.append(e);
			buf.append("; ");
		}
		return buf.toString();
	}

	@Override
	public long getValid() {
		return valid.get();
	}

	@Override
	public long getInvalid() {
		return invalid.get();
	}

	protected abstract List<Schema> getSchemas();
	protected abstract Source getMessageBody(InputStream input) throws Exception;
	protected abstract Response createErrorResponse(String message);
	protected abstract boolean isFault(Message msg);
	protected abstract String getPreliminaryError(XOPReconstitutor xopr, Message msg);

}
