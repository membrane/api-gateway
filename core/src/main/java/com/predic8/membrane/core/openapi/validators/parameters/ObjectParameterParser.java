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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.util.JsonUtil.*;

/**
 * TODO implement for objects in parameters
 */
public class ObjectParameterParser extends AbstractParameterParser {

    private static final Logger log = LoggerFactory.getLogger(ObjectParameterParser.class.getName());

    @Override
    public JsonNode getJson() throws JsonProcessingException, AdditionalPropertiesException {
        List<String> values = getValuesForParameter();
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
            // foo= => foo: "" => Let assume an empty parameter is an empty array
            if (tokens.getFirst().isEmpty()) {
                return FACTORY.nullNode();
            }
        }
        ObjectNode obj = FACTORY.objectNode();
        while (!tokens.isEmpty()) {
            String fieldName = tokens.pollFirst();

            JsonNode json = scalarAsJson(tokens.pollFirst());

            Schema<?> parameterSchema = OpenAPIUtil.resolveSchema(api, parameter);

            // If parameter is listed as a property of the object
            if (OpenAPIUtil.getProperty(parameterSchema, fieldName) != null) {
                obj.set(fieldName, json);
                continue;
            }

            Object additionalProperties = parameterSchema.getAdditionalProperties();

            // Default for additionalProperties is true
            if (additionalProperties == null) {
                obj.replace(fieldName, json);
                return obj;
            }

            // additionalProperties is true or false
            if (additionalProperties instanceof
                    Boolean apb) {
                if (apb.booleanValue()) {
                    obj.replace(fieldName, json);
                    continue;
                }
            }

            if (!(additionalProperties instanceof
                    Schema aps))
                return obj;

            // Schema but boolean
            if (aps.getBooleanSchemaValue() != null) {
                if (aps.getBooleanSchemaValue()) {
                    obj.replace(fieldName, json);
                    continue;
                }
            }

            // Validate additional property against Schema
            ValidationErrors errors = new SchemaValidator(api, aps).validate(new ValidationContext(), json);
            if (!errors.hasErrors()) {
                obj.replace(fieldName, json);
                continue;
            }

            log.debug("Field '{}' does not conform to schema for additionalProperties", fieldName);
        }
        return obj;
    }
}