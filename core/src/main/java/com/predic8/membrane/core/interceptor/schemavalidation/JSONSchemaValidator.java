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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.Resolver;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.StreamSupport.stream;

public class JSONSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(JSONSchemaValidator.class);

    private static final ObjectMapper om = new ObjectMapper();

    private JsonSchema schema;
    private final Resolver resolver;
    private final String jsonSchema;
    private final ValidatorInterceptor.FailureHandler failureHandler;
    private final ErrorDetailsPolicy errorDetailsPolicy;

    private final AtomicLong valid = new AtomicLong();
    private final AtomicLong invalid = new AtomicLong();

    public JSONSchemaValidator(Resolver resolver, String jsonSchema, ValidatorInterceptor.FailureHandler failureHandler) {
        this(resolver, jsonSchema, failureHandler, ErrorDetailsPolicy.FULL);
    }

    public JSONSchemaValidator(Resolver resolver, String jsonSchema, ValidatorInterceptor.FailureHandler failureHandler, ErrorDetailsPolicy errorDetailsPolicy) {
        this.resolver = resolver;
        this.jsonSchema = jsonSchema;
        this.failureHandler = failureHandler;
        this.errorDetailsPolicy = errorDetailsPolicy;
    }

    @Override
    public String getName() {
        return "JSON Schema Validator";
    }

    @Override
    public void init() {
        super.init();
        createValidators();
    }

    public Outcome validateMessage(Exchange exc, Flow flow) throws Exception {
        return validateMessage(exc, flow, UTF_8);
    }

    public Outcome validateMessage(Exchange exc, Flow flow, Charset ignored) throws Exception {

        Message msg = exc.getMessage(flow);

        List<String> errors;
        try {
            ProcessingReport report = schema.validateUnchecked(om.readTree(msg.getBodyAsStreamDecoded()));
            if (report.isSuccess()) {
                valid.incrementAndGet();
                return CONTINUE;
            }
            errors = getErrors(report);
        } catch (JsonParseException e) {
            errors = List.of(e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage());
        }

        return reportFailure(exc, flow, msg, errors);
    }

    private Outcome reportFailure(Exchange exc, Flow flow, Message msg, List<String> errors) {
        invalid.incrementAndGet();
        log.info("{} message did not validate against {}: {}", flow, jsonSchema, errors);

        if (failureHandler != null) {
            failureHandler.handleFailure(getErrorString(msg, errors), exc);
        }

        errorDetailsPolicy.problemDetails(getName(), getErrorTitle(),
                        pd -> pd.topLevel("flow", flow.name()).topLevel("errors", errors))
                .buildAndSetResponse(exc);

        return ABORT;
    }

    private static @NotNull List<String> getErrors(ProcessingReport report) {
        return stream(report.spliterator(), false).map(ProcessingMessage::getMessage).toList();
    }

    private @NotNull String getErrorString(Message msg, List<String> errors) {
        StringBuilder message = new StringBuilder();
        message.append(getSourceOfError(msg));
        message.append(": ");
        for (String error : errors) {
            message.append(error);
            message.append(";");
        }
        return message.toString();
    }

    private void createValidators() {
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        try {
            schema = factory.getJsonSchema(om.readTree(resolver.resolve(jsonSchema)));
        } catch (Exception e) {
            throw new ConfigurationException("Cannot create JSON Schema Validator for Schema: %s".formatted(jsonSchema), e);
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

    @Override
    public String getErrorTitle() {
        return "JSON validation failed";
    }
}