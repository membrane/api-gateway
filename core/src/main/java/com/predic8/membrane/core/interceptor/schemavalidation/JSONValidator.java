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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.resolver.ResolverMap;

public class JSONValidator implements IValidator {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private JsonSchema schema;
	private final ResolverMap resourceResolver;
	private final String jsonSchema;
	private final ValidatorInterceptor.FailureHandler failureHandler;
	
	private final AtomicLong valid = new AtomicLong();
	private final AtomicLong invalid = new AtomicLong();
	
	public JSONValidator(ResolverMap resourceResolver, String jsonSchema, ValidatorInterceptor.FailureHandler failureHandler) throws IOException {
		this.resourceResolver = resourceResolver;
		this.jsonSchema = jsonSchema;
		this.failureHandler = failureHandler;
		createValidators();
	}
	
	public Outcome validateMessage(Exchange exc, Message msg, String source) throws Exception {
		return validateMessage(exc, msg.getBodyAsStreamDecoded(), Charset.forName(msg.getCharset()), source);
	}
	
	public Outcome validateMessage(Exchange exc, InputStream body, Charset charset, String source) throws Exception {
		List<String> errors;
		boolean success = true;
		try {
			JsonNode node = JsonLoader.fromReader(new InputStreamReader(body, charset));
			ProcessingReport report = schema.validateUnchecked(node);
			success = report.isSuccess();
			errors = new ArrayList<String>();
			for (ProcessingMessage message : report)
				errors.add(message.getMessage());
		} catch (JsonParseException e) {
			success = false;
			errors = new ArrayList<String>();
			errors.add(e.getMessage());
		}
        
        if (success) {
        	valid.incrementAndGet();
        	return Outcome.CONTINUE;
        }
        
        if (failureHandler == FailureHandler.VOID) {
        	StringBuilder message = new StringBuilder();
        	message.append(source);
        	message.append(": ");
        	for (String error : errors) {
        		message.append(error);
        		message.append(";");
        	}
        	exc.setProperty("error", message.toString());
        	invalid.incrementAndGet();
        	return Outcome.ABORT;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator jg = new JsonFactory().createGenerator(baos);

        jg.writeStartObject();
        jg.writeStringField("source", source);
        jg.writeArrayFieldStart("errors");
        for (String message : errors)
        	jg.writeString(message);
        jg.close();
        	
		if (failureHandler != null) {
			failureHandler.handleFailure(new String(baos.toByteArray(), UTF8), exc);
			exc.setResponse(Response.badRequest().
					contentType("application/json;charset=utf-8").
					body("{\"error\":\"error\"}".getBytes(UTF8)).
					build());
		} else {
	        exc.setResponse(Response.badRequest().
	        		contentType("application/json;charset=utf-8").
	        		body(baos.toByteArray()).
	        		build());
		}
		
        invalid.incrementAndGet();
		return Outcome.ABORT;
	}

	
	private void createValidators() throws IOException {
		JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
		JsonNode schemaNode = JsonLoader.fromReader(new InputStreamReader(resourceResolver.resolve(jsonSchema)));
		try {
			schema = factory.getJsonSchema(schemaNode);
		} catch (ProcessingException e) {
			throw new IOException(e);
		}
	}

	@Override
	public long getValid() {
		return valid.get();
	}

	@Override
	public long getInvalid() {
		return invalid.get();
	}

}