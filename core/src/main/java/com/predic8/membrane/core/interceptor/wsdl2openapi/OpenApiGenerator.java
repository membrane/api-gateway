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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import com.predic8.membrane.core.util.wsdl.parser.Message;
import com.predic8.membrane.core.util.wsdl.parser.Operation;
import com.predic8.membrane.core.util.wsdl.parser.Service;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.INPUT;
import static com.predic8.membrane.core.util.wsdl.parser.Operation.Direction.OUTPUT;
import static io.swagger.v3.parser.ObjectMapperFactory.createYaml;
import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

/**
 * Generates an OpenAPI 3.0 model from WSDL definitions.
 */
public class OpenApiGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenApiGenerator.class);

    private final Definitions definitions;
    private final String basePath;
    private final XsdToSchema converter;
    private final Map<String, OperationConfig> operations;

    public OpenApiGenerator(Definitions definitions, String basePath) {
        this(definitions, basePath, Map.of());
    }

    public OpenApiGenerator(Definitions definitions, String basePath, Map<String, OperationConfig> operations) {
        this.definitions = definitions;
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        this.converter = new XsdToSchema(definitions);
        this.operations = operations;
    }

    public OpenAPI generate() {
        var openAPI = new OpenAPI();
        openAPI.setOpenapi("3.0.0");
        openAPI.setInfo(buildInfo());
        openAPI.setServers(List.of(new Server().url(basePath)));
        openAPI.setPaths(buildPaths());
        return openAPI;
    }

    public String generateYaml() {
        try {
            return createYaml().writeValueAsString(generate());
        } catch (Exception e) {
            throw new RuntimeException("Could not serialize OpenAPI model to YAML", e);
        }
    }

    private Info buildInfo() {
        return new Info()
                .title(getServiceName())
                .description("Auto-generated OpenAPI from WSDL")
                .version("1.0.0");
    }

    private Paths buildPaths() {
        var paths = new Paths();
        if (operations.isEmpty()) {
            definitions.getPortTypes().stream()
                    .flatMap(pt -> pt.getOperations().stream())
                    .filter(wsdlOp -> {
                        if (wsdlOp.getName() == null) {
                            log.debug("Skipping WSDL operation with null name");
                            return false;
                        }
                        return true;
                    })
                    .forEach(wsdlOp -> paths.addPathItem("/" + camelToKebab(wsdlOp.getName()), buildPathItem(wsdlOp.getName(), wsdlOp)));
        } else {
            var wsdlOps = definitions.getPortTypes().stream()
                    .flatMap(pt -> pt.getOperations().stream())
                    .filter(op -> op.getName() != null)
                    .toList();
            for (var name : operations.keySet()) {
                var wsdlOp = wsdlOps.stream().filter(op -> name.equals(op.getName())).findFirst().orElse(null);
                if (wsdlOp == null) log.debug("Configured operation '{}' not found in WSDL definitions", name);
                paths.addPathItem("/" + camelToKebab(name), buildPathItem(name, wsdlOp));
            }
        }
        return paths;
    }

    private PathItem buildPathItem(String name, Operation wsdlOp) {
        var inputs = wsdlOp != null ? wsdlOp.getMessagesByDirection(INPUT) : List.<Message>of();
        var outputs = wsdlOp != null ? wsdlOp.getMessagesByDirection(OUTPUT) : List.<Message>of();
        var apiOp = new io.swagger.v3.oas.models.Operation()
                .operationId(name)
                .summary(name)
                .requestBody(buildRequestBody(inputs))
                .responses(buildResponses(outputs));
        return new PathItem().post(apiOp);
    }

    private RequestBody buildRequestBody(List<Message> messages) {
        var schema = converter.convertMessageParts(messages);
        return new RequestBody()
                .required(true)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private ApiResponses buildResponses(List<Message> messages) {
        var schema = converter.convertMessageParts(messages);
        return new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .description("Successful response")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(schema))))
                .addApiResponse("500", new ApiResponse().description("Internal server error"));
    }

    private String getServiceName() {
        List<Service> services = definitions.getServices();
        if (!services.isEmpty()) {
            return services.getFirst().getName();
        }
        return "SOAP Service";
    }

    private String camelToKebab(String camelCase) {
        var result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (isUpperCase(c)) {
                if (i > 0) result.append('-');
                result.append(toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
