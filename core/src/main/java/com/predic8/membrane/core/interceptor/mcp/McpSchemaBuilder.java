/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpSchemaBuilder {

    private McpSchemaBuilder() {
    }

    static ObjectSchemaBuilder object() {
        return new ObjectSchemaBuilder();
    }

    static PropertySchemaBuilder integer() {
        return new PropertySchemaBuilder("integer");
    }

    static PropertySchemaBuilder string() {
        return new PropertySchemaBuilder("string");
    }

    static PropertySchemaBuilder bool() {
        return new PropertySchemaBuilder("boolean");
    }

    static class PropertySchemaBuilder {
        final Map<String, Object> schema = new LinkedHashMap<>();

        PropertySchemaBuilder(String type) {
            schema.put("type", type);
        }

        PropertySchemaBuilder minimum(int minimum) {
            schema.put("minimum", minimum);
            return this;
        }

        PropertySchemaBuilder maximum(int maximum) {
            schema.put("maximum", maximum);
            return this;
        }

        PropertySchemaBuilder description(String description) {
            schema.put("description", description);
            return this;
        }

        Map<String, Object> build() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(schema));
        }
    }

    static final class ObjectSchemaBuilder extends PropertySchemaBuilder {
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private List<String> required = List.of();
        private Boolean additionalProperties;

        ObjectSchemaBuilder() {
            super("object");
        }

        ObjectSchemaBuilder property(String name, PropertySchemaBuilder propertySchema) {
            properties.put(name, propertySchema.build());
            return this;
        }

        ObjectSchemaBuilder required(String... required) {
            this.required = List.of(required);
            return this;
        }

        ObjectSchemaBuilder additionalProperties(boolean additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        @Override
        Map<String, Object> build() {
            Map<String, Object> objectSchema = new LinkedHashMap<>(schema);
            objectSchema.put("properties", Collections.unmodifiableMap(new LinkedHashMap<>(properties)));
            if (!required.isEmpty()) {
                objectSchema.put("required", List.copyOf(required));
            }
            if (additionalProperties != null) {
                objectSchema.put("additionalProperties", additionalProperties);
            }
            return Collections.unmodifiableMap(objectSchema);
        }
    }
}
