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

import com.fasterxml.jackson.databind.*;
import com.github.fge.jsonschema.core.report.*;
import com.github.fge.jsonschema.main.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.StreamSupport.*;

public class JSONSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(JSONSchemaValidator.class);

    private static final ObjectMapper om = new ObjectMapper();

    private JsonSchema schema;
    private final Resolver resolver;
    private final String jsonSchema;
    private final ValidatorInterceptor.FailureHandler failureHandler;

    private final AtomicLong valid = new AtomicLong();
    private final AtomicLong invalid = new AtomicLong();

    public JSONSchemaValidator(Resolver resolver, String jsonSchema, ValidatorInterceptor.FailureHandler failureHandler) {
        this.resolver = resolver;
        this.jsonSchema = jsonSchema;
        this.failureHandler = failureHandler;
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

        ProcessingReport report = schema.validateUnchecked(om.readTree(msg.getBodyAsStreamDecoded()));
        if (report.isSuccess()) {
            valid.incrementAndGet();
            return CONTINUE;
        }

        List<String> errors = getErrors(report);

        // What is that for? A property "error" is accessed in the elasticsearchstore?
        if (failureHandler == FailureHandler.VOID) {
            exc.setProperty("error", getErrorString(msg, errors));
            invalid.incrementAndGet();
            return ABORT;
        }

        if (failureHandler != null) {
            failureHandler.handleFailure(getErrorString(msg, errors), exc);
            user(false,getName())
                    .title(getErrorTitle())
                    .addSubType("validation")
                    .buildAndSetResponse(exc);
            invalid.incrementAndGet();
            return ABORT;
        }

        user(false,getName())
                .title(getErrorTitle())
                .addSubType("validation")
                .component(getName())
                .internal("flow", flow.name())
                .internal("errors", errors)
                .buildAndSetResponse(exc);

        invalid.incrementAndGet();
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