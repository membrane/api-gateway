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
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.util.JsonUtil.*;

public class ExplodedObjectParameterParser extends AbstractParameterParser {
    @Override
    public JsonNode getJson() throws JsonProcessingException {
        ObjectNode obj = FACTORY.objectNode();

        var schema = resolveSchema(api, parameter);
        if (schema == null) {
            return obj;
        }

        Set<String> known = new HashSet<>();
        explicitProperties(schema, obj, known);
        patternProperties(schema, obj, known);

        Object additional = schema.getAdditionalProperties();
        if (additional == null) {
            return obj;
        }

        if (additionalBoolean(additional, known, obj)) return obj;
        additionalSchemaProperties(additional, known, obj);
        return obj;
    }

    private void explicitProperties(Schema<?> schema, ObjectNode obj, Set<String> known) {
        if (schema.getProperties() == null)
            return;

        schema.getProperties().forEach((propName, propSchema) -> {
            var paramValues = values.get(propName);
            if (paramValues != null && !paramValues.isEmpty()) {
                obj.set(propName, scalarAsJson(paramValues.getFirst()));
                known.add(propName);
            }
        });

    }

    private void additionalSchemaProperties(Object additional, Set<String> known, ObjectNode obj) {
        if (!(additional instanceof Schema<?> aps))
            return;

        SchemaValidator validator = new SchemaValidator(api, aps);
        values.forEach((k, vs) -> {
            if (known.contains(k) || vs == null || vs.isEmpty()) return;
            var json = scalarAsJson(vs.getFirst());
            ValidationErrors errs = validator.validate(new ValidationContext(), json);
            if (!errs.hasErrors()) {
                obj.set(k, json);
            }
        });

    }

    private boolean additionalBoolean(Object additional, Set<String> known, ObjectNode obj) {
        if (!(additional instanceof Boolean apb)) {
            return false;
        }

        if (apb) {
            values.forEach((k, vs) -> {
                if (!known.contains(k) && vs != null && !vs.isEmpty()) {
                    obj.set(k, scalarAsJson(vs.getFirst()));
                }
            });
        }
        return true;
    }

    private void patternProperties(Schema<?> schema, ObjectNode obj, Set<String> known) {
        if (schema.getPatternProperties() == null || schema.getPatternProperties().isEmpty())
            return;

        schema.getPatternProperties().forEach((regex, patSchema) -> {
            Pattern pattern = Pattern.compile(regex);
            values.forEach((k, vs) -> {
                if (pattern.matcher(k).matches() && vs != null && !vs.isEmpty()) {
                    obj.set(k, scalarAsJson(vs.getFirst()));
                    known.add(k);
                }
            });
        });
    }
}