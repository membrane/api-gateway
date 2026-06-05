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

package com.predic8.membrane.core.interceptor.json.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.resource.SchemaLoader;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.schemavalidation.json.MembraneSchemaLoader;
import com.predic8.membrane.core.resolver.Resolver;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.networknt.schema.InputFormat.JSON;
import static com.networknt.schema.InputFormat.YAML;
import static com.networknt.schema.SchemaRegistry.withDefaultDialect;
import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;

@MCElement(name = "schemaValidation", component = false)
public class JsonRPCSchemaValidation {

    private static final ObjectMapper OM = new ObjectMapper();

    private JsonRPCErrorValidation errorValidation;
    private JsonRPCMethodDefinitions methods = new JsonRPCMethodDefinitions();
    private Schema errorSchema;
    private Map<String, Schema> paramSchemas = Map.of();
    private Map<String, Schema> responseSchemas = Map.of();

    public JsonRPCErrorValidation getErrorValidation() {
        return errorValidation;
    }

    @MCChildElement(order = 1)
    public void setErrorValidation(JsonRPCErrorValidation errorValidation) {
        this.errorValidation = errorValidation;
    }

    @MCChildElement(order = 2)
    public void setMethods(JsonRPCMethodDefinitions methods) {
        this.methods = methods == null ? new JsonRPCMethodDefinitions() : methods;
    }

    public JsonRPCMethodDefinitions getMethods() {
        return methods;
    }

    public void init(Resolver resolver, URIFactory uriFactory, String beanBaseLocation) {
        if (resolver == null || uriFactory == null) {
            throw new ConfigurationException("Cannot initialize JSON-RPC schema validation without resolver context.");
        }

        SchemaRegistry registry = createSchemaRegistry(resolver);
        errorSchema = resolveErrorSchema(registry, resolver, uriFactory, beanBaseLocation);

        Map<String, Schema> resolvedParamSchemas = new LinkedHashMap<>();
        Map<String, Schema> resolvedResponseSchemas = new LinkedHashMap<>();

        for (Map.Entry<String, JsonRPCSchemas> entry : methods.getMethods().entrySet()) {
            String methodName = validateMethodName(entry.getKey());
            JsonRPCSchemas definitions = requireDefinitions(methodName, entry.getValue());

            resolveMethodSchema(
                    resolvedParamSchemas,
                    registry,
                    methodName,
                    "params",
                    definitions.getParams(),
                    resolver,
                    uriFactory,
                    beanBaseLocation
            );
            resolveMethodSchema(
                    resolvedResponseSchemas,
                    registry,
                    methodName,
                    "response",
                    definitions.getResponse(),
                    resolver,
                    uriFactory,
                    beanBaseLocation
            );
        }

        paramSchemas = Collections.unmodifiableMap(resolvedParamSchemas);
        responseSchemas = Collections.unmodifiableMap(resolvedResponseSchemas);
    }

    public boolean hasRequestValidation() {
        return !paramSchemas.isEmpty();
    }

    public boolean hasMethodResponseValidation() {
        return !responseSchemas.isEmpty();
    }

    public boolean hasErrorValidation() {
        return errorSchema != null;
    }

    public boolean hasResponseValidation() {
        return hasMethodResponseValidation() || hasErrorValidation();
    }

    public boolean isEmpty() {
        return !hasRequestValidation() && !hasResponseValidation();
    }

    public Schema getParamSchema(String methodName) {
        if (methodName == null) {
            return null;
        }
        return paramSchemas.get(methodName);
    }

    public Schema getResponseSchema(String methodName) {
        if (methodName == null) {
            return null;
        }
        return responseSchemas.get(methodName);
    }

    public Schema getErrorSchema() {
        return errorSchema;
    }

    private Schema resolveErrorSchema(SchemaRegistry registry, Resolver resolver, URIFactory uriFactory, String beanBaseLocation) {
        if (errorValidation == null) {
            return null;
        }

        String location = normalizeLocation(errorValidation.getLocation());
        if (location == null) {
            throw new ConfigurationException("JSON-RPC error schema validation must define a non-empty location.");
        }

        return loadSchema(
                "JSON-RPC error schema",
                registry,
                SchemaLocation.of(combine(uriFactory, beanBaseLocation, location)),
                resolver,
                location
        );
    }

    private void resolveMethodSchema(Map<String, Schema> target,
                                     SchemaRegistry registry,
                                     String methodName,
                                     String schemaRole,
                                     SchemaSetter definition,
                                     Resolver resolver,
                                     URIFactory uriFactory,
                                     String beanBaseLocation) {
        Schema schema = resolveConfiguredSchema(registry, methodName, schemaRole, definition, resolver, uriFactory, beanBaseLocation);
        if (schema != null) {
            target.put(methodName, schema);
        }
    }

    private Schema resolveConfiguredSchema(SchemaRegistry registry,
                                           String methodName,
                                           String schemaRole,
                                           SchemaSetter definition,
                                           Resolver resolver,
                                           URIFactory uriFactory,
                                           String beanBaseLocation) {
        if (definition == null) {
            return null;
        }

        String location = normalizeLocation(definition.getLocation());
        boolean hasLocation = location != null;
        boolean hasInlineSchema = definition.getSchema() != null;

        if (hasLocation == hasInlineSchema) {
            throw new ConfigurationException(
                    "JSON-RPC %s schema for method '%s' must define exactly one of 'location' or 'schema'."
                            .formatted(schemaRole, methodName)
            );
        }

        if (hasLocation) {
            return loadSchema(
                    "JSON-RPC %s schema for method '%s'".formatted(schemaRole, methodName),
                    registry,
                    SchemaLocation.of(combine(uriFactory, beanBaseLocation, location)),
                    resolver,
                    location
            );
        }

        return loadInlineSchema(registry, methodName, schemaRole, definition.getSchema(), uriFactory, beanBaseLocation);
    }

    private Schema loadInlineSchema(SchemaRegistry registry,
                                    String methodName,
                                    String schemaRole,
                                    JsonRPCInlineSchema inlineSchema,
                                    URIFactory uriFactory,
                                    String beanBaseLocation) {
        if (inlineSchema == null || inlineSchema.getProperties().isEmpty()) {
            throw new ConfigurationException(
                    "JSON-RPC %s schema for method '%s' must not be empty."
                            .formatted(schemaRole, methodName)
            );
        }

        try {
            Schema schema = registry.getSchema(
                    SchemaLocation.of(createInlineSchemaLocation(methodName, schemaRole, uriFactory, beanBaseLocation)),
                    OM.valueToTree(inlineSchema.getProperties())
            );
            schema.initializeValidators();
            return schema;
        } catch (RuntimeException e) {
            throw new ConfigurationException(
                    "Cannot create inline JSON-RPC %s schema for method '%s'."
                            .formatted(schemaRole, methodName),
                    e
            );
        }
    }

    private String createInlineSchemaLocation(String methodName, String schemaRole, URIFactory uriFactory, String beanBaseLocation) {
        String syntheticFile = "__jsonrpc_%s_%s.schema.json".formatted(
                sanitize(methodName),
                sanitize(schemaRole)
        );
        if (beanBaseLocation == null || beanBaseLocation.isBlank()) {
            return "membrane:%s".formatted(syntheticFile);
        }
        return combine(uriFactory, beanBaseLocation, syntheticFile);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Schema loadSchema(String description,
                              SchemaRegistry registry,
                              SchemaLocation schemaLocation,
                              Resolver resolver,
                              String configuredLocation) {
        try (var in = resolver.resolve(schemaLocation.getAbsoluteIri().toString())) {
            Schema schema = registry.getSchema(schemaLocation, in, getSchemaFormat(schemaLocation.getAbsoluteIri().toString()));
            schema.initializeValidators();
            return schema;
        } catch (IOException e) {
            throw new ConfigurationException("Cannot read %s from '%s'.".formatted(description, configuredLocation), e);
        } catch (RuntimeException e) {
            throw new ConfigurationException("Cannot create %s from '%s'.".formatted(description, configuredLocation), e);
        }
    }

    private static SchemaRegistry createSchemaRegistry(Resolver resolver) {
        return withDefaultDialect(
                DRAFT_2020_12,
                builder -> builder.schemaLoader(SchemaLoader.builder()
                        .resourceLoaders(loaders -> loaders.values(list -> list.addFirst(new MembraneSchemaLoader(resolver))))
                        .build())
        );
    }

    private static InputFormat getSchemaFormat(String schemaLocation) {
        String normalized = schemaLocation.toLowerCase();
        return normalized.endsWith(".yaml") || normalized.endsWith(".yml") ? YAML : JSON;
    }

    private static JsonRPCSchemas requireDefinitions(String methodName, JsonRPCSchemas definitions) {
        if (definitions == null) {
            throw new ConfigurationException("JSON-RPC schema validation entry for method '%s' must not be null.".formatted(methodName));
        }
        return definitions;
    }

    private static String validateMethodName(String methodName) {
        if (methodName == null || methodName.trim().isEmpty()) {
            throw new ConfigurationException("JSON-RPC method name must not be empty.");
        }
        return methodName.trim();
    }

    private static String normalizeLocation(String location) {
        if (location == null) {
            return null;
        }
        String trimmed = location.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
