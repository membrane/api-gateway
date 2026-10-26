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
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.media.*;
import org.slf4j.*;

import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.util.json.JsonUtil.*;
import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;

public class ExplodedObjectParameterParser extends AbstractParameterParser {

    private static final Logger log = LoggerFactory.getLogger(ExplodedObjectParameterParser.class);

    private Set<String> otherParameterNames = Set.of();

    @Override
    public JsonNode getJson() throws ParameterParsingException {
        var obj = FACTORY.objectNode();
        var schema = resolveSchema(api, parameter);
        if (schema == null) {
            return obj;
        }

        Set<String> known = new HashSet<>();
        explicitProperties(schema, obj, known);
        patternProperties(schema, obj, known);

        var additional = schema.getAdditionalProperties();
        if (additional == null) {
            return obj;
        }

        // AdditionalProperties is boolean, handle here and exit
        if (additionalBoolean(additional, known, obj))
            return obj;

        additionalSchemaProperties(additional, known, obj);
        return obj;
    }

    private void explicitProperties(Schema<?> schema, ObjectNode obj, Set<String> known) {
        if (schema.getProperties() == null)
            return;

        schema.getProperties().forEach((propName, propSchema) -> {
            var paramValues = values.get(propName);
            if (paramValues != null && !paramValues.isEmpty()) {
                obj.set(propName, scalarAsJson(decode(paramValues.getFirst(), UTF_8)));
                known.add(propName);
            }
        });

    }

    private void additionalSchemaProperties(Object additional, Set<String> known, ObjectNode obj) {
        if (!(additional instanceof Schema<?> aps))
            return;

        var validator = new SchemaValidator(api, aps);
        values.forEach((k, vs) -> {
            if (known.contains(k) || vs == null || vs.isEmpty()) return;
            var json = scalarAsJson(decode(vs.getFirst(),UTF_8));
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
                    obj.set(k, scalarAsJson(decode(vs.getFirst(),UTF_8)));
                }
            });
        }
        return true;
    }

    private void patternProperties(Schema<?> schema, ObjectNode obj, Set<String> known) {
        final var patterns = compilePatternProperties(schema);
        if (patterns.isEmpty())
            return;

        values.forEach((k, vs) -> {
            if (known.contains(k)) return; // avoid double set
            if (otherParameterNames.contains(k)) return; // belongs to another declared parameter
            if (vs != null && !vs.isEmpty() && matchesAny(patterns, k)) {
                obj.set(k, scalarAsJson(decode(vs.getFirst(), UTF_8)));
                known.add(k);
            }
        });
    }

    /**
     * Names of the other query parameters declared for the operation. They are never collected
     * via patternProperties, so an unanchored pattern cannot claim e.g. a declared <code>limit</code>.
     */
    public void setOtherParameterNames(Set<String> otherParameterNames) {
        this.otherParameterNames = Set.copyOf(otherParameterNames);
    }

    /**
     * Compiles the patternProperties regexes of the schema. Invalid regexes are logged and skipped.
     */
    public static List<Pattern> compilePatternProperties(Schema<?> schema) {
        if (schema.getPatternProperties() == null)
            return List.of();

        final var patterns = new ArrayList<Pattern>();
        schema.getPatternProperties().keySet().forEach(regex -> {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                log.warn("Ignoring invalid patternProperties regex '{}': {}", regex, e.getMessage());
            }
        });
        return List.copyOf(patterns);
    }

    /**
     * JSON Schema regexes are not anchored: a name matches if the pattern matches anywhere in it.
     */
    public static boolean matchesAny(List<Pattern> patterns, String name) {
        return patterns.stream().anyMatch(p -> p.matcher(name).find());
    }
}
