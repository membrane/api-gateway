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

package com.predic8.membrane.core.interceptor.schemavalidation.json;

import com.github.fge.jsonschema.*;
import com.networknt.schema.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.networknt.schema.InputFormat.JSON;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

public class JSONYAMLSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(JSONYAMLSchemaValidator.class);

    private final Resolver resolver;
    private final String jsonSchema;
    private final FailureHandler failureHandler;

    private final AtomicLong valid = new AtomicLong();
    private final AtomicLong invalid = new AtomicLong();
    private final SpecVersion.VersionFlag schemaId;

    /**
     * JsonSchemaFactory instances are thread-safe provided its configuration is not modified.
     */
    JsonSchemaFactory jsonSchemaFactory;

    SchemaValidatorsConfig config;

    /**
     * JsonSchema instances are thread-safe provided its configuration is not modified.
     */
    JsonSchema schema;

    /**
     * Construct a JSONYAMLSchemaValidator configured with a resolver, the JSON Schema source, a failure handler, and the schema version.
     *
     * @param resolver       resolver used to load referenced schemas and resources
     * @param jsonSchema     the schema location or JSON/YAML schema content to validate against
     * @param failureHandler handler used to build problem-detail responses on validation failures
     * @param schemaVersion  JSON Schema version identifier (e.g., "2020-12"); parsed into the validator's internal schemaId
     */
    public JSONYAMLSchemaValidator(Resolver resolver, String jsonSchema, FailureHandler failureHandler, String schemaVersion) {
        this.resolver = resolver;
        this.jsonSchema = jsonSchema;
        this.failureHandler = failureHandler;
        this.schemaId = JSONSchemaVersionParser.parse( schemaVersion);
    }

    /**
     * Creates a JSONYAMLSchemaValidator using the default JSON Schema version "2020-12".
     *
     * @param resolver         resolver used to load external schemas or resources referenced by the JSON Schema
     * @param jsonSchema       schema location or the schema content to validate messages against
     * @param failureHandler   handler used to build and set problem-detail responses for validation failures
     */
    public JSONYAMLSchemaValidator(Resolver resolver, String jsonSchema, FailureHandler failureHandler) {
        this(resolver, jsonSchema, failureHandler, "2020-12");
    }

    /**
     * Provides the validator's display name.
     *
     * @return the name "JSON Schema Validator"
     */
    @Override
    public String getName() {
        return "JSON Schema Validator";
    }

    /**
     * Initializes the JSON Schema validation infrastructure for this validator.
     *
     * <p>Configures a thread-safe JsonSchemaFactory (using the configured schema version and the
     * instance's Resolver), builds the SchemaValidatorsConfig, loads the JsonSchema from the
     * configured schema location, and initializes its validators so the schema instance is ready
     * for concurrent validation calls.</p>
     */
    @Override
    public void init() {
        super.init();

        jsonSchemaFactory = JsonSchemaFactory.getInstance(schemaId, builder ->
                builder.schemaLoaders(loaders -> loaders.add(new MembraneSchemaLoader(resolver)))
               // builder.schemaMappers(schemaMappers -> schemaMappers.mapPrefix("https://www.example.org/", "classpath:/"))
        );

        SchemaValidatorsConfig.Builder builder = SchemaValidatorsConfig.builder();
        // By default the JDK regular expression implementation which is not ECMA 262 compliant is used
        // Note that setting this requires including optional dependencies
        // builder.regularExpressionFactory(GraalJSRegularExpressionFactory.getInstance());
        // builder.regularExpressionFactory(JoniRegularExpressionFactory.getInstance());
        config = builder.build();

        // If the schema data does not specify an $id the absolute IRI of the schema location will be used as the $id.
        schema=  jsonSchemaFactory.getSchema(SchemaLocation.of( jsonSchema), config);
        schema.initializeValidators();

    }

    /**
     * Validates the message body of the given exchange against the configured JSON Schema using UTF-8 encoding.
     *
     * @param exc the exchange containing the message to validate
     * @param flow the flow (REQUEST/RESPONSE) indicating which message direction to validate
     * @return an Outcome indicating whether processing should continue (`CONTINUE`) or be aborted (`ABORT`)
     */
    public Outcome validateMessage(Exchange exc, Flow flow) throws Exception {
        return validateMessage(exc, flow, UTF_8);
    }

    /**
     * Validates the message body for the given flow against the configured JSON Schema and sets a problem-details
     * response on the exchange when validation fails.
     *
     * @param exc     the exchange containing the message to validate
     * @param flow    the flow (e.g. REQUEST or RESPONSE) whose message is validated
     * @param ignored unused charset parameter (kept for API compatibility)
     * @return        `CONTINUE` if the message conforms to the schema, `ABORT` if validation failed and a
     *                problem-details response was set on the exchange
     */
    public Outcome validateMessage(Exchange exc, Flow flow, Charset ignored) throws Exception {

        Set<ValidationMessage> assertions = schema.validate(exc.getMessage(flow).getBodyAsStringDecoded(), JSON);

        if (assertions.isEmpty()) {
            valid.incrementAndGet();
            return CONTINUE;
        }
        invalid.incrementAndGet();

        log.debug("Validation failed: {}", assertions);

        user(false, getName())
                .title(getErrorTitle())
                .addSubType("validation")
                .component(getName())
                .internal("flow", flow.name())
                .internal("errors", getMapForProblemDetails(assertions))
                .buildAndSetResponse(exc);

        return ABORT;
    }

    /**
     * Convert a set of schema validation messages into a list of problem-detail maps.
     *
     * @param assertions the validation messages produced by JSON Schema validation
     * @return a list of maps where each map contains problem-detail fields derived from a validation message
     */
    private @NotNull List<Map<String, Object>> getMapForProblemDetails(Set<ValidationMessage> assertions) {
        return assertions.stream().map(this::validationMessageToProblemDetailsMap).toList();
    }

    /**
     * Converts a ValidationMessage into an ordered map of problem-detail fields suitable for inclusion
     * in a Problem Details response.
     *
     * @param vm the ValidationMessage to convert
     * @return a LinkedHashMap with the following keys:
     *         "message" (human-readable message),
     *         "code" (validation code),
     *         "key" (message key),
     *         "details" (optional additional details),
     *         "type" (message type),
     *         "error" (error identifier),
     *         "pointer" (RFC 6901 JSON Pointer to the failing location),
     *         "node" (the JSON node instance related to the message)
     */
    private @NotNull Map<String, Object> validationMessageToProblemDetailsMap(ValidationMessage vm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", vm.getMessage());
        m.put("code", vm.getCode());
        m.put("key", vm.getMessageKey());
        if (vm.getDetails() != null)
            m.put("details", vm.getDetails());
        m.put("type", vm.getType());
        m.put("error", vm.getError());
        m.put("pointer", getPointer(vm.getEvaluationPath()));
        m.put("node", vm.getInstanceNode());
        return m;
    }

    /**
     * Builds an RFC 6901 JSON Pointer string for the given evaluation path.
     *
     * @param evaluationPath the JSON node path to convert; may be null or empty
     * @return the JSON Pointer string (empty string if the path is null or has no components)
     */
    private String getPointer(JsonNodePath evaluationPath) {
        if (evaluationPath == null || evaluationPath.getNameCount() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < evaluationPath.getNameCount(); i++) {
            sb.append('/');
            String part = evaluationPath.getName(i);

            // escape according to RFC 6901
            part = part.replace("~", "~0").replace("/", "~1");

            sb.append(part);
        }
        return sb.toString();
    }

    /**
     * Provides the count of successfully validated messages.
     *
     * @return the number of messages that passed validation
     */
    @Override
    public long getValid() {
        return valid.get();
    }

    /**
     * Current count of messages that failed JSON Schema validation.
     *
     * @return the number of messages that failed validation
     */
    @Override
    public long getInvalid() {
        return invalid.get();
    }

    /**
     * Provides the human-readable title used for JSON validation error responses.
     *
     * @return the error title "JSON validation failed"
     */
    @Override
    public String getErrorTitle() {
        return "JSON validation failed";
    }
}