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

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.resource.SchemaLoader;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.core.interceptor.schemavalidation.json.MembraneSchemaLoader;
import com.predic8.membrane.core.resolver.Resolver;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.networknt.schema.InputFormat.JSON;
import static com.networknt.schema.InputFormat.YAML;
import static com.networknt.schema.SchemaRegistry.withDefaultDialect;
import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;

@MCElement(name = "params", component = false)
public class JsonRPCParams {

    private Map<String, String> params = new LinkedHashMap<>();
    private List<CompiledSchema> schemas = List.of();

    @MCOtherAttributes
    public void setParams(Map<String, String> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void init(Resolver resolver, URIFactory uriFactory, String beanBaseLocation) {
        if (params.isEmpty()) {
            schemas = List.of();
            return;
        }
        if (resolver == null || uriFactory == null) {
            throw new ConfigurationException("Cannot initialize JSON-RPC param schemas without resolver context.");
        }

        schemas = params.entrySet().stream()
                .map(entry -> new CompiledSchema(
                        entry.getKey(),
                        compilePattern(entry.getKey()),
                        loadSchema(entry.getKey(), entry.getValue(), resolver, uriFactory, beanBaseLocation)
                ))
                .toList();
    }

    public Schema getSchema(String method) {
        if (method == null) {
            return null;
        }

        for (CompiledSchema schema : schemas) {
            if (schema.pattern().matcher(method).matches()) {
                return schema.schema();
            }
        }
        return null;
    }

    private static Schema loadSchema(String methodPattern, String schemaPath, Resolver resolver, URIFactory uriFactory, String beanBaseLocation) {
        if (schemaPath == null || schemaPath.trim().isEmpty()) {
            throw new ConfigurationException("JSON-RPC param schema path for method pattern '%s' must not be empty.".formatted(methodPattern));
        }

        String resolvedLocation = combine(uriFactory, beanBaseLocation, schemaPath.trim());
        try (InputStream in = resolver.resolve(resolvedLocation)) {
            Schema schema = createSchemaRegistry(resolver).getSchema(
                    SchemaLocation.of(resolvedLocation),
                    in,
                    getSchemaFormat(resolvedLocation)
            );
            schema.initializeValidators();
            return schema;
        } catch (IOException e) {
            throw new ConfigurationException("Cannot read JSON-RPC param schema for method pattern '%s' from '%s'.".formatted(methodPattern, schemaPath), e);
        } catch (RuntimeException e) {
            throw new ConfigurationException("Cannot create JSON-RPC param schema for method pattern '%s' from '%s'.".formatted(methodPattern, schemaPath), e);
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
        return schemaLocation.toLowerCase().endsWith(".yaml") || schemaLocation.toLowerCase().endsWith(".yml") ? YAML : JSON;
    }

    private static Pattern compilePattern(String methodPattern) {
        if (methodPattern == null || methodPattern.trim().isEmpty()) {
            throw new ConfigurationException("JSON-RPC param method pattern must not be empty.");
        }
        try {
            return Pattern.compile(methodPattern.trim());
        } catch (PatternSyntaxException e) {
            throw new ConfigurationException("Invalid JSON-RPC param method regex: " + methodPattern, e);
        }
    }

    private record CompiledSchema(String methodPattern, Pattern pattern, Schema schema) {
    }
}
