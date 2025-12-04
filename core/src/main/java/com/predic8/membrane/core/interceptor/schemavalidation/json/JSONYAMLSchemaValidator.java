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

import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.path.NodePath;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.schemavalidation.AbstractMessageValidator;
import com.predic8.membrane.core.interceptor.schemavalidation.ValidatorInterceptor.FailureHandler;
import com.predic8.membrane.core.resolver.Resolver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.networknt.schema.InputFormat.JSON;
import static com.networknt.schema.InputFormat.YAML;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS;

public class JSONYAMLSchemaValidator extends AbstractMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(JSONYAMLSchemaValidator.class);

    private final YAMLFactory factory = YAMLFactory.builder().enable(STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper yamlObjectMapper = YAMLMapper.builder(factory).disable(FAIL_ON_TRAILING_TOKENS).build();
    private final ObjectMapper jsonObjectMapper = JsonMapper.builder().build();

    private static final com.fasterxml.jackson.databind.ObjectMapper legacyObjectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public static final String SCHEMA_VERSION_2020_12 = "2020-12";

    private final Resolver resolver;
    private final String jsonSchema;
    private final FailureHandler failureHandler;

    private final AtomicLong valid = new AtomicLong();
    private final AtomicLong invalid = new AtomicLong();
    private final SpecificationVersion schemaId;

    /**
     * JsonSchemaFactory instances are thread-safe provided its configuration is not modified.
     */
    SchemaRegistry jsonSchemaFactory;

    /**
     * JsonSchema instances are thread-safe provided its configuration is not modified.
     */
    Schema schema;

    InputFormat inputFormat;

    public JSONYAMLSchemaValidator(Resolver resolver, String jsonSchema, FailureHandler failureHandler, String schemaVersion, InputFormat inputFormat) {
        this.resolver = resolver;
        this.jsonSchema = jsonSchema;
        this.failureHandler = failureHandler;
        this.schemaId = JSONSchemaVersionParser.parse(schemaVersion);
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

        jsonSchemaFactory = SchemaRegistry.withDefaultDialect(schemaId, builder ->
                builder.schemaLoader(loaders -> new MembraneSchemaLoader(resolver)));

        try (InputStream in = resolver.resolve(jsonSchema)) {
            // Bridge to com.fasterxml for networknt
            schema = jsonSchemaFactory.getSchema(legacyObjectMapper.readTree(((jsonSchema.endsWith(".yaml") || jsonSchema.endsWith(".yml")) ? yamlObjectMapper : jsonObjectMapper).readTree(in).toString()));
            schema.initializeValidators();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read JSON Schema from: " + jsonSchema, e);
        }
    }

    public Outcome validateMessage(Exchange exc, Flow flow) throws Exception {
        return validateMessage(exc, flow, UTF_8);
    }

    public Outcome validateMessage(Exchange exc, Flow flow, Charset ignored) throws Exception {

        List<Error> assertions = inputFormat == YAML ?
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
    private @NotNull List<Error> handleMultipleYAMLDocuments(Exchange exc, Flow flow) throws IOException {
        List<Error> assertions = new ArrayList<>();
        JsonParser parser = factory.createParser(exc.getMessage(flow).getBodyAsStreamDecoded());
        while (!parser.isClosed()) {
            tools.jackson.databind.JsonNode node3 = yamlObjectMapper.readTree(parser);
            if (node3 != null) {
                // Bridge to com.fasterxml for networknt
                assertions.addAll(schema.validate(legacyObjectMapper.readTree(node3.toString())));
            }
            parser.nextToken();
        }
        return assertions;
    }

    private @NotNull List<Map<String, Object>> getMapForProblemDetails(List<Error> assertions) {
        return assertions.stream().map(this::validationMessageToProblemDetailsMap).toList();
    }

    private @NotNull Map<String, Object> validationMessageToProblemDetailsMap(Error vm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", vm.getMessage());
        m.put("key", vm.getMessageKey());
        if (vm.getDetails() != null)
            m.put("details", vm.getDetails());
        m.put("keyword", vm.getKeyword());
        m.put("pointer", getPointer(vm.getEvaluationPath()));
        m.put("node", vm.getInstanceNode());
        return m;
    }

    private String getPointer(NodePath evaluationPath) {
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