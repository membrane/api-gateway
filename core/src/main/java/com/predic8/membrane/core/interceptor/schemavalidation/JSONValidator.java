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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.github.fge.jackson.*;
import com.github.fge.jsonschema.core.exceptions.*;
import com.github.fge.jsonschema.core.report.*;
import com.github.fge.jsonschema.main.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.*;
import com.predic8.membrane.core.resolver.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static java.nio.charset.StandardCharsets.*;

public class JSONValidator extends AbstractMessageValidator {

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
	}

	@Override
	public String getName() {
		return "JSON Schema Validator";
	}

	@Override
	public void init() throws IOException {
		createValidators();
	}

	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		return validateMessage(exc, msg, Charset.forName(msg.getCharset()));
	}

	public Outcome validateMessage(Exchange exc, Message msg, Charset charset) throws Exception {

		InputStream body = msg.getBodyAsStreamDecoded();

		List<String> errors = new ArrayList<>();
		boolean success;
		try {
			ProcessingReport report = schema.validateUnchecked(JsonLoader.fromReader(new InputStreamReader(body, charset)));
			success = report.isSuccess();
			for (ProcessingMessage message : report)
				errors.add(message.getMessage());
		} catch (JsonParseException e) {
			success = false;
			errors.add(e.getMessage());
		}

		if (success) {
			valid.incrementAndGet();
			return Outcome.CONTINUE;
		}

		if (failureHandler == FailureHandler.VOID) {
			StringBuilder message = new StringBuilder();
			message.append(getSourceOfError(msg));
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
		jg.writeStringField("source", getSourceOfError(msg));
		jg.writeArrayFieldStart("errors");
		for (String message : errors)
			jg.writeString(message);
		jg.close();

		if (failureHandler != null) {
			failureHandler.handleFailure(baos.toString(UTF_8), exc);
			exc.setResponse(Response.badRequest().
					contentType("application/json;charset=utf-8").
					body("{\"error\":\"error\"}".getBytes(UTF_8)).
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