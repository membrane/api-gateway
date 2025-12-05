/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.*;
import static com.predic8.membrane.core.util.json.JsonUtil.*;
import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;

/**
 * Parser for form-style object parameters (non-exploded).
 *
 */
public class ObjectParameterParser extends AbstractParameterParser {

    private static final Logger log = LoggerFactory.getLogger(ObjectParameterParser.class.getName());

    @Override
    public JsonNode getJson() throws ParameterParsingException {
        var values = getValues();
        if (values == null || values.isEmpty())
            return FACTORY.objectNode();

        Deque<String> tokens = new ArrayDeque<>();

        Collections.addAll(tokens, values.getFirst().split(","));
        if (tokens.isEmpty()) {
            return FACTORY.objectNode();
        }
        if (tokens.size() == 1) {
            if ("null".equals(tokens.getFirst())) {
                return FACTORY.nullNode();
            }
            // foo= => interpret empty parameter as JSON null for object type
            if (tokens.getFirst().isEmpty()) {
                return FACTORY.nullNode();
            }
        }
        var obj = FACTORY.objectNode();

        var parameterSchema = OpenAPIUtil.resolveSchema(api, parameter);
        if (parameterSchema == null) {
                return FACTORY.objectNode();
        }
        while (!tokens.isEmpty()) {
            String fieldName = Optional.ofNullable(tokens.pollFirst()).orElse("").trim();
            if (fieldName.isEmpty()) continue;
            var json = scalarAsJson(decode(requireNonNullElse(tokens.pollFirst(), NULL), UTF_8));

            // If parameter is listed as a property of the object
            if (OpenAPIUtil.getProperty(parameterSchema, fieldName) != null) {
                obj.set(fieldName, json);
                continue;
            }

            var additionalProperties = parameterSchema.getAdditionalProperties();

            // Default for additionalProperties is true
            if (additionalProperties == null) {
                obj.set(fieldName, json);
                continue;
            }

            // additionalProperties is true or false
            if (additionalProperties instanceof Boolean apb) {
                if (apb) {
                    obj.set(fieldName, json);
                }
                continue;
            }

            if (!(additionalProperties instanceof Schema<?> aps))
                continue;

            // Schema but boolean
            if (aps.getBooleanSchemaValue() != null) {
                if (aps.getBooleanSchemaValue()) {
                    obj.set(fieldName, json);
                }
                continue;
            }

            // Validate additional property against Schema
            var errors = new SchemaValidator(api, aps).validate(new ValidationContext(), json);
            if (!errors.hasErrors()) {
                obj.set(fieldName, json);
                continue;
            }

            log.debug("Field '{}' does not conform to schema for additionalProperties. Errors: {}", fieldName, errors);
        }
        return obj;
    }

}