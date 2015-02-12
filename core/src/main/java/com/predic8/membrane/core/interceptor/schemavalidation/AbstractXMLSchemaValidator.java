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

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.multipart.XOPReconstitutor;
import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.schema.Schema;

public abstract class AbstractXMLSchemaValidator implements IValidator {
	private static Log log = LogFactory.getLog(AbstractXMLSchemaValidator.class.getName());

	private final ArrayBlockingQueue<List<Validator>> validators;
	protected final XOPReconstitutor xopr;
	protected final String location;
	protected final ResolverMap resourceResolver;
	protected final ValidatorInterceptor.FailureHandler failureHandler;
	private final boolean skipFaults;
	
	protected final AtomicLong valid = new AtomicLong();
	protected final AtomicLong invalid = new AtomicLong();

	public AbstractXMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler) throws Exception {
		this(resourceResolver, location, failureHandler, false);
	}

	public AbstractXMLSchemaValidator(ResolverMap resourceResolver, String location, ValidatorInterceptor.FailureHandler failureHandler, boolean skipFaults) throws Exception {
		this.location = location;
		this.resourceResolver = resourceResolver;
		this.failureHandler = failureHandler;
		this.skipFaults = skipFaults;
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		validators = new ArrayBlockingQueue<List<Validator>>(concurrency);
		for (int i = 0; i < concurrency; i++)
			validators.add(createValidators());
		xopr = new XOPReconstitutor();
	}
	
	public Outcome validateMessage(Exchange exc, Message msg, String source) throws Exception {
		List<Exception> exceptions = new ArrayList<Exception>();
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
							return Outcome.CONTINUE;
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
			return Outcome.CONTINUE;
		}
		if (failureHandler == FailureHandler.VOID) {
			exc.setProperty("error", getErrorMsg(exceptions));
		} else if (failureHandler != null) {
			failureHandler.handleFailure(getErrorMsg(exceptions), exc);
			exc.setResponse(createErrorResponse("validation error"));
		} else {
			exc.setResponse(createErrorResponse(getErrorMsg(exceptions)));
			exc.getResponse().getHeader().add(Header.VALIDATION_ERROR_SOURCE, source);
		}
		invalid.incrementAndGet();
		return Outcome.ABORT;
	}
	
	protected List<Validator> createValidators() throws Exception {
		SchemaFactory sf = SchemaFactory.newInstance(Constants.XSD_NS);
		List<Validator> validators = new ArrayList<Validator>();
		for (Schema schema : getSchemas()) {
			log.debug("Creating validator for schema: " + schema);
			StreamSource ss = new StreamSource(new StringReader(schema.getAsString()));
			ss.setSystemId(location);
			sf.setResourceResolver(resourceResolver.toLSResourceResolver());
			Validator validator = sf.newSchema(ss).newValidator();
			validator.setResourceResolver(resourceResolver.toLSResourceResolver());
			validator.setErrorHandler(new SchemaValidatorErrorHandler());
			validators.add(validator);
		}
		return validators;
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
