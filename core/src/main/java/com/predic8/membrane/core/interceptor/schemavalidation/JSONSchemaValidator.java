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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.resolver.Resolver;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.networknt.schema.InputFormat.JSON;
import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Validates a JSON message body against a JSON Schema using the networknt validator. Used by the
 * Kubernetes admission validation, which passes a {@code null} failure handler and reads the
 * resulting Problem Details {@code errors} list back off the exchange.
 */
public class JSONSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(JSONSchemaValidator.class);

    private static final ObjectMapper om = new ObjectMapper();

    // The retrieval URI used when registering the (self-contained) schema. Schemas carrying their
    // own $id override it; inline schemas without $id resolve internal $refs against it.
    private static final String SCHEMA_RETRIEVAL_URI = "https://membrane-soa.org/json-schema-validator";

    private Schema schema;
    private final Resolver resolver;
    private final String jsonSchema;
    private final FailureHandler failureHandler;

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

        List<Error> result = schema.validate(om.readTree(msg.getBodyAsStreamDecoded()));
        if (result.isEmpty()) {
            valid.incrementAndGet();
            return CONTINUE;
        }

        List<String> errors = getErrors(result);

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

    private static @NotNull List<String> getErrors(List<Error> result) {
        return result.stream().map(Error::getMessage).toList();
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
        try (InputStream in = resolver.resolve(jsonSchema)) {
            schema = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, b -> {})
                    .getSchema(SchemaLocation.of(SCHEMA_RETRIEVAL_URI), in, JSON);
            schema.initializeValidators();
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
