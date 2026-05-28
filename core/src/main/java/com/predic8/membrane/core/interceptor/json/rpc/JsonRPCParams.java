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
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCOtherAttributes;
import com.predic8.membrane.annot.Required;
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

/**
 * @description
 * <p>Maps JSON-RPC method name patterns to JSON Schema locations for validating the
 * <code>params</code> member of a request.</p>
 *
 * <p>In YAML, the configuration is expressed as a map from method regex to schema location.
 * In XML, use repeated <code>param</code> child elements with <code>method</code> and
 * <code>schema</code> attributes. Entries are checked in order and the first matching
 * method pattern wins. Inline schemas are not supported.</p>
 */
@MCElement(name = "params", component = false)
public class JsonRPCParams {

    private Map<String, String> params = new LinkedHashMap<>();
    private List<Param> paramMappings = List.of();
    private List<CompiledSchema> schemas = List.of();

    /**
     * @description
     * <p>Defines a map from method name regex to JSON Schema location.</p>
     *
     * <p>The insertion order is preserved and determines precedence when multiple regexes match.</p>
     *
     * @example "^rpc\\.echo$": "classpath:/json/rpc/echo-params.schema.json"
     */
    @MCOtherAttributes
    public void setParams(Map<String, String> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    public Map<String, String> getParams() {
        return params;
    }

    /**
     * @description
     * <p>Defines XML child elements for method-to-schema mappings.</p>
     *
     * <p>This form is intended for XML configuration. YAML keeps using the map syntax shown above.</p>
     *
     * @example &lt;param method="^rpc\\.echo$" schema="classpath:/json/rpc/echo-params.schema.json"/&gt;
     */
    @MCChildElement(excludeFromJson = true)
    public void setParamMappings(List<Param> paramMappings) {
        this.paramMappings = paramMappings == null ? List.of() : List.copyOf(paramMappings);
    }

    public List<Param> getParamMappings() {
        return paramMappings;
    }

    public void init(Resolver resolver, URIFactory uriFactory, String beanBaseLocation) {
        List<Param> effectiveMappings = getEffectiveMappings();
        if (effectiveMappings.isEmpty()) {
            schemas = List.of();
            return;
        }
        if (resolver == null || uriFactory == null) {
            throw new ConfigurationException("Cannot initialize JSON-RPC param schemas without resolver context.");
        }

        schemas = effectiveMappings.stream()
                .map(entry -> new CompiledSchema(
                        entry.getMethod(),
                        compilePattern(entry.getMethod()),
                        loadSchema(entry.getMethod(), entry.getSchema(), resolver, uriFactory, beanBaseLocation)
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

    private List<Param> getEffectiveMappings() {
        if (!params.isEmpty() && !paramMappings.isEmpty()) {
            throw new ConfigurationException("Configure JSON-RPC params either as a YAML map or as XML <param> child elements, not both.");
        }
        if (!paramMappings.isEmpty()) {
            return paramMappings;
        }
        return params.entrySet().stream()
                .map(entry -> new Param(entry.getKey(), entry.getValue()))
                .toList();
    }

    @MCElement(name = "param", component = false)
    public static class Param {

        private String method;
        private String schema;

        public Param() {
        }

        public Param(String method, String schema) {
            this.method = method;
            this.schema = schema;
        }

        /**
         * @description The regular expression matched against the JSON-RPC <code>method</code> value.
         * @example ^rpc\\.echo$
         */
        @Required
        @MCAttribute
        public void setMethod(String method) {
            this.method = method;
        }

        public String getMethod() {
            return method;
        }

        /**
         * @description The path or URL of the JSON Schema used to validate <code>params</code> for matching methods.
         * @example classpath:/json/rpc/echo-params.schema.json
         */
        @Required
        @MCAttribute
        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getSchema() {
            return schema;
        }
    }
}
