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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.networknt.schema.*;
import com.networknt.schema.serialization.YamlMapperFactory;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.schemavalidation.*;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.IOException;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static com.networknt.schema.InputFormat.JSON;
import static com.networknt.schema.InputFormat.YAML;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

public class JSONYAMLSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(JSONYAMLSchemaValidator.class);
    private final YAMLFactory factory = YAMLFactory.builder().enable(STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper objectMapper = new ObjectMapper(factory);

    public static final String SCHEMA_VERSION_2020_12 = "2020-12";

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

    InputFormat inputFormat;

    public JSONYAMLSchemaValidator(Resolver resolver, String jsonSchema, FailureHandler failureHandler, String schemaVersion, InputFormat inputFormat) {
        this.resolver = resolver;
        this.jsonSchema = jsonSchema;
        this.failureHandler = failureHandler;
        this.schemaId = JSONSchemaVersionParser.parse( schemaVersion);
        this.inputFormat = inputFormat;
    }

    public JSONYAMLSchemaValidator(Resolver resolver, String jsonSchema, FailureHandler failureHandler, String schemaVersion) {
        this(resolver, jsonSchema, failureHandler, schemaVersion, JSON);
    }

    public JSONYAMLSchemaValidator(Resolver resolver, String jsonSchema, FailureHandler failureHandler) {
        this(resolver, jsonSchema, failureHandler, SCHEMA_VERSION_2020_12);
    }

    @Override
    public String getName() {
        return "JSON Schema Validator";
    }

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

    public Outcome validateMessage(Exchange exc, Flow flow) throws Exception {
        return validateMessage(exc, flow, UTF_8);
    }

    public Outcome validateMessage(Exchange exc, Flow flow, Charset ignored) throws Exception {

        Set<ValidationMessage> assertions = inputFormat == YAML ?
            handleMultipleYAMLDocuments(exc, flow) :
            schema.validate(exc.getMessage(flow).getBodyAsStringDecoded(), inputFormat);

        if (assertions.isEmpty()) {
            valid.incrementAndGet();
            return CONTINUE;
        }
        invalid.incrementAndGet();


        log.debug("Validation failed: {}", assertions);

        List<Map<String, Object>> mapForProblemDetails = getMapForProblemDetails(assertions);
        failureHandler.handleFailure(mapForProblemDetails.toString(), exc);

        user(false, getName())
                .title(getErrorTitle())
                .addSubType("validation")
                .component(getName())
                .internal("flow", flow.name())
                .internal("errors", mapForProblemDetails)
                .buildAndSetResponse(exc);

        return ABORT;
    }

    /**
     * If you call schema.validate(..) on a multi-document YAML, only the first document is validated. Therefore, we have
     * to loop here ourselves.
     */
    private @NotNull Set<ValidationMessage> handleMultipleYAMLDocuments(Exchange exc, Flow flow) throws IOException {
        Set<ValidationMessage> assertions;
        assertions = new LinkedHashSet<>();
        YAMLParser parser = factory.createParser(exc.getMessage(flow).getBodyAsStreamDecoded());
        while (!parser.isClosed()) {
            assertions.addAll(schema.validate(objectMapper.readTree(parser)));
            parser.nextToken();
        }
        return assertions;
    }

    private @NotNull List<Map<String, Object>> getMapForProblemDetails(Set<ValidationMessage> assertions) {
        return assertions.stream().map(this::validationMessageToProblemDetailsMap).toList();
    }

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