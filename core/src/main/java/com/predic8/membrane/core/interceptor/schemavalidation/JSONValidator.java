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

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.eel.kitchen.jsonschema.main.JsonSchema;
import org.eel.kitchen.jsonschema.main.JsonSchemaFactory;
import org.eel.kitchen.jsonschema.report.ValidationReport;
import org.eel.kitchen.jsonschema.util.JsonLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.util.ResourceResolver;

public class JSONValidator implements IValidator {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private JsonSchema schema;
	private final ResourceResolver resourceResolver;
	private final String jsonSchema;
	private final ValidatorInterceptor.FailureHandler failureHandler;
	
	private final AtomicLong valid = new AtomicLong();
	private final AtomicLong invalid = new AtomicLong();
	
	public JSONValidator(ResourceResolver resourceResolver, String jsonSchema, ValidatorInterceptor.FailureHandler failureHandler) throws IOException {
		this.resourceResolver = resourceResolver;
		this.jsonSchema = jsonSchema;
		this.failureHandler = failureHandler;
		createValidators();
	}
	
	public Outcome validateMessage(Exchange exc, Message msg, String source) throws Exception {
		return validateMessage(exc, msg.getBodyAsStream(), Charset.forName(msg.getCharset()), source);
	}
	
	public Outcome validateMessage(Exchange exc, InputStream body, Charset charset, String source) throws Exception {
		List<String> errors;
		boolean success = true;
		try {
			JsonNode node = JsonLoader.fromReader(new InputStreamReader(body, charset));
			ValidationReport report = schema.validate(node);
			success = report.isSuccess();
			errors = report.getMessages();
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
        JsonGenerator jg = new JsonFactory().createJsonGenerator(baos);

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
		JsonSchemaFactory factory = JsonSchemaFactory.defaultFactory();
		JsonNode schemaNode = JsonLoader.fromReader(new InputStreamReader(resourceResolver.resolve(jsonSchema)));
		schema = factory.fromSchema(schemaNode);
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