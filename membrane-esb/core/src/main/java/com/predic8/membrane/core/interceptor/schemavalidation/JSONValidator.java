package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.eel.kitchen.jsonschema.main.JsonValidationFailureException;
import org.eel.kitchen.jsonschema.main.JsonValidator;
import org.eel.kitchen.jsonschema.main.ValidationConfig;
import org.eel.kitchen.jsonschema.main.ValidationReport;
import org.eel.kitchen.util.JsonLoader;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.util.ResourceResolver;

public class JSONValidator implements IValidator {
	private static final Log log = LogFactory.getLog(JSONValidator.class.getName());
	private static final Charset UTF8 = Charset.forName("UTF-8");

	// Since JsonValidator is not thread-safe, we simply allocate 2*#CPU and use
	// one exclusively for each validation request.
	private ArrayBlockingQueue<JsonValidator> validators;
	private final ResourceResolver resourceResolver;
	private final String jsonSchema;
	private final ValidatorInterceptor.FailureHandler failureHandler;
	
	private final AtomicLong valid = new AtomicLong();
	private final AtomicLong invalid = new AtomicLong();
	
	public JSONValidator(ResourceResolver resourceResolver, String jsonSchema, ValidatorInterceptor.FailureHandler failureHandler) throws IOException, JsonValidationFailureException {
		this.resourceResolver = resourceResolver;
		this.jsonSchema = jsonSchema;
		this.failureHandler = failureHandler;
		createValidators();
	}
	
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception {
		List<String> errors;
		boolean success = true;
		try {
			JsonNode node = JsonLoader.fromReader(new InputStreamReader(msg.getBodyAsStream()));
			JsonValidator validator = validators.take();
			ValidationReport report;
			try {
				report = validator.validate(node);
			} finally {
				validators.put(validator);
			}
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

	
	private void createValidators() throws IOException, JsonValidationFailureException {
		int concurrency = Runtime.getRuntime().availableProcessors() * 2;
		validators = new ArrayBlockingQueue<JsonValidator>(concurrency);
		for (int i = 0; i < concurrency; i++) {
			JsonNode schemaNode = new ObjectMapper().readTree(resourceResolver.resolve(jsonSchema));
			ValidationConfig cfg = new ValidationConfig();
			JsonValidator validator = new JsonValidator(cfg, schemaNode);
			if (i == 0) {
				ValidationReport report = validator.validateSchema();
				for (String message : report.getMessages())
					log.error(message);
			}
			validators.add(validator);
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